package com.seafile.seadroid2.ui.transfer_list;

import androidx.lifecycle.MutableLiveData;

import com.blankj.utilcode.util.CollectionUtils;
import com.blankj.utilcode.util.FileUtils;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.SupportAccountManager;
import com.seafile.seadroid2.framework.data.db.AppDatabase;
import com.seafile.seadroid2.framework.data.db.entities.FileTransferEntity;
import com.seafile.seadroid2.framework.data.model.enums.TransferAction;
import com.seafile.seadroid2.framework.data.model.enums.TransferDataSource;
import com.seafile.seadroid2.framework.data.model.enums.TransferResult;
import com.seafile.seadroid2.framework.data.model.enums.TransferStatus;
import com.seafile.seadroid2.framework.worker.BackgroundJobManagerImpl;
import com.seafile.seadroid2.framework.worker.SupportWorkManager;
import com.seafile.seadroid2.framework.worker.UploadFileManuallyWorker;
import com.seafile.seadroid2.framework.worker.UploadFolderFileAutomaticallyWorker;
import com.seafile.seadroid2.framework.worker.UploadMediaFileAutomaticallyWorker;
import com.seafile.seadroid2.ui.base.viewmodel.BaseViewModel;
import com.seafile.seadroid2.framework.util.SLogs;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.SingleSource;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

public class TransferListViewModel extends BaseViewModel {

    private MutableLiveData<List<FileTransferEntity>> mFileTransferEntitiesLiveData = new MutableLiveData<>();

    public MutableLiveData<List<FileTransferEntity>> getFileTransferEntitiesLiveData() {
        return mFileTransferEntitiesLiveData;
    }

    public void loadData(TransferAction transferAction, boolean isShowRefresh) {
        if (isShowRefresh) {
            getRefreshLiveData().setValue(true);
        }

        Account account = SupportAccountManager.getInstance().getCurrentAccount();

        if (account == null) {
            getRefreshLiveData().setValue(false);
            return;
        }

        Single<List<FileTransferEntity>> single;
        if (TransferAction.UPLOAD == transferAction) {
            single = AppDatabase
                    .getInstance()
                    .fileTransferDAO()
                    .getUploadListAsync(account.getSignature());
        } else {
            single = AppDatabase
                    .getInstance()
                    .fileTransferDAO()
                    .getDownloadListAsync(account.getSignature());
        }


        addSingleDisposable(single, new Consumer<List<FileTransferEntity>>() {
            @Override
            public void accept(List<FileTransferEntity> fileTransferEntities) throws Exception {
                mFileTransferEntitiesLiveData.setValue(fileTransferEntities);

                if (isShowRefresh) {
                    getRefreshLiveData().setValue(false);
                }
            }
        });
    }

    public void deleteTransferData(FileTransferEntity fileTransferEntity, TransferAction transferAction, Consumer<Boolean> consumer) {

        Single<Boolean> single = Single.create(new SingleOnSubscribe<Boolean>() {
            @Override
            public void subscribe(SingleEmitter<Boolean> emitter) throws Exception {

                if (TransferDataSource.DOWNLOAD == fileTransferEntity.data_source) {
                    if (fileTransferEntity.transfer_status == TransferStatus.IN_PROGRESS) {
                        BackgroundJobManagerImpl.getInstance().cancelFilesDownloadJob();
                    }

                    AppDatabase.getInstance().fileTransferDAO().deleteOne(fileTransferEntity);
                } else if (TransferDataSource.FILE_BACKUP == fileTransferEntity.data_source) {
                    if (fileTransferEntity.transfer_status == TransferStatus.IN_PROGRESS) {
                        BackgroundJobManagerImpl.getInstance().cancelById(UploadFileManuallyWorker.UID);
                    }

                    AppDatabase.getInstance().fileTransferDAO().deleteOne(fileTransferEntity);
                    FileUtils.delete(fileTransferEntity.full_path);

                    BackgroundJobManagerImpl.getInstance().startFileUploadWorker();

                } else if (TransferDataSource.FOLDER_BACKUP == fileTransferEntity.data_source) {
                    BackgroundJobManagerImpl.getInstance().cancelById(UploadFolderFileAutomaticallyWorker.UID);

                    fileTransferEntity.data_status = -1;
                    fileTransferEntity.transfer_result = TransferResult.NO_RESULT;
                    fileTransferEntity.transfer_status = TransferStatus.CANCELLED;
                    fileTransferEntity.transferred_size = 0;

                    AppDatabase.getInstance().fileTransferDAO().insert(fileTransferEntity);

                    BackgroundJobManagerImpl.getInstance().startFolderUploadWorker();

                } else if (TransferDataSource.ALBUM_BACKUP == fileTransferEntity.data_source) {
                    BackgroundJobManagerImpl.getInstance().cancelById(UploadMediaFileAutomaticallyWorker.UID);

                    fileTransferEntity.data_status = -1;
                    fileTransferEntity.transfer_result = TransferResult.NO_RESULT;
                    fileTransferEntity.transfer_status = TransferStatus.CANCELLED;
                    fileTransferEntity.transferred_size = 0;

                    AppDatabase.getInstance().fileTransferDAO().insert(fileTransferEntity);

                    BackgroundJobManagerImpl.getInstance().startMediaBackupWorker();
                }
                emitter.onSuccess(true);
            }
        });

        addSingleDisposable(single, new Consumer<Boolean>() {
            @Override
            public void accept(Boolean aBoolean) throws Exception {
                if (transferAction == TransferAction.DOWNLOAD) {
                    FileUtils.delete(fileTransferEntity.target_path);
                }

                consumer.accept(true);
            }
        });
    }

    public void cancelAllUploadTask(Consumer<Boolean> consumer) {
        Account account = SupportAccountManager.getInstance().getCurrentAccount();
        List<TransferDataSource> dataSources = CollectionUtils.newArrayList(TransferDataSource.ALBUM_BACKUP, TransferDataSource.FOLDER_BACKUP);
        Completable completable = AppDatabase.getInstance().fileTransferDAO().cancelByDataSource(account.getSignature(), dataSources);
        addCompletableDisposable(completable, new Action() {
            @Override
            public void run() throws Exception {
                consumer.accept(true);
            }
        });
    }

    public void cancelAllDownloadTask(Consumer<Boolean> consumer) {
        Account account = SupportAccountManager.getInstance().getCurrentAccount();
        List<TransferDataSource> dataSources = CollectionUtils.newArrayList(TransferDataSource.DOWNLOAD);
        Completable completable = AppDatabase.getInstance().fileTransferDAO().cancelByDataSource(account.getSignature(), dataSources);
        addCompletableDisposable(completable, new Action() {
            @Override
            public void run() throws Exception {
                consumer.accept(true);
            }
        });
    }

    public void removeAllDownloadTask(Consumer<Boolean> consumer) {
        Account account = SupportAccountManager.getInstance().getCurrentAccount();
        List<TransferDataSource> features = CollectionUtils.newArrayList(TransferDataSource.DOWNLOAD);
        Single<List<FileTransferEntity>> single = AppDatabase.getInstance().fileTransferDAO().getListByFeatAsync(account.getSignature(), features);

        Single<Boolean> single1 = single.flatMap(new Function<List<FileTransferEntity>, SingleSource<Boolean>>() {
            @Override
            public SingleSource<Boolean> apply(List<FileTransferEntity> entities) throws Exception {
                return Single.create(new SingleOnSubscribe<Boolean>() {
                    @Override
                    public void subscribe(SingleEmitter<Boolean> emitter) throws Exception {

                        for (FileTransferEntity entity : entities) {
                            //delete record
                            AppDatabase.getInstance().fileTransferDAO().deleteOne(entity);

                            if (entity.transfer_action == TransferAction.DOWNLOAD) {
                                FileUtils.delete(entity.target_path);
                                SLogs.d("deleted : " + entity.target_path);
                            }
                        }

                        emitter.onSuccess(true);
                    }
                });
            }
        });

        addSingleDisposable(single1, new Consumer<Boolean>() {
            @Override
            public void accept(Boolean b) throws Exception {
                if (consumer != null) {
                    consumer.accept(true);
                }
            }
        });
    }

    public void removeSpecialUploadListTask(List<FileTransferEntity> list, Consumer<Boolean> consumer) {
        Single<Boolean> single = Single.create(new SingleOnSubscribe<Boolean>() {
            @Override
            public void subscribe(SingleEmitter<Boolean> emitter) throws Exception {

                for (FileTransferEntity entity : list) {
                    entity.data_status = -1;
                    entity.transfer_result = TransferResult.NO_RESULT;
                    entity.transfer_status = TransferStatus.CANCELLED;
                    entity.transferred_size = 0;

                    AppDatabase.getInstance().fileTransferDAO().insert(entity);
                }

                emitter.onSuccess(true);
            }
        });

        addSingleDisposable(single, new Consumer<Boolean>() {
            @Override
            public void accept(Boolean aBoolean) throws Exception {
                if (consumer != null) {
                    consumer.accept(true);
                }
            }
        });
    }

    public void removeSpecialDownloadListTask(List<FileTransferEntity> list, Consumer<Boolean> consumer) {
        Single<Boolean> single = Single.create(new SingleOnSubscribe<Boolean>() {
            @Override
            public void subscribe(SingleEmitter<Boolean> emitter) throws Exception {

                for (FileTransferEntity entity : list) {
                    //delete record
                    AppDatabase.getInstance().fileTransferDAO().deleteOne(entity);

                    FileUtils.delete(entity.target_path);
                    SLogs.d("deleted : " + entity.target_path);
                }

                emitter.onSuccess(true);
            }
        });

        addSingleDisposable(single, new Consumer<Boolean>() {
            @Override
            public void accept(Boolean aBoolean) throws Exception {
                if (consumer != null) {
                    consumer.accept(true);
                }
            }
        });
    }

    public void removeAllUploadTask(Consumer<Boolean> consumer) {
        Account account = SupportAccountManager.getInstance().getCurrentAccount();
        List<TransferDataSource> features = CollectionUtils.newArrayList(TransferDataSource.FILE_BACKUP, TransferDataSource.FOLDER_BACKUP);
        Single<List<FileTransferEntity>> single = AppDatabase.getInstance().fileTransferDAO().getListByFeatAsync(account.getSignature(), features);

        Single<Boolean> single1 = single.flatMap(new Function<List<FileTransferEntity>, SingleSource<Boolean>>() {
            @Override
            public SingleSource<Boolean> apply(List<FileTransferEntity> entities) throws Exception {
                return Single.create(new SingleOnSubscribe<Boolean>() {
                    @Override
                    public void subscribe(SingleEmitter<Boolean> emitter) throws Exception {

                        for (FileTransferEntity entity : entities) {
                            entity.data_status = -1;
                            entity.transfer_result = TransferResult.NO_RESULT;
                            entity.transfer_status = TransferStatus.CANCELLED;
                            entity.transferred_size = 0;

                            AppDatabase.getInstance().fileTransferDAO().insert(entity);

                            if (entity.transfer_action == TransferAction.DOWNLOAD) {
                                FileUtils.delete(entity.target_path);
                                SLogs.d("deleted : " + entity.target_path);
                            }
                        }

                        emitter.onSuccess(true);
                    }
                });
            }
        });

        addSingleDisposable(single1, new Consumer<Boolean>() {
            @Override
            public void accept(Boolean b) throws Exception {
                if (consumer != null) {
                    consumer.accept(true);
                }
            }
        });
    }

    public void restartUpload(List<FileTransferEntity> list, Consumer<Boolean> consumer) {
        Single<Boolean> single = Single.create(new SingleOnSubscribe<Boolean>() {
            @Override
            public void subscribe(SingleEmitter<Boolean> emitter) throws Exception {

                for (FileTransferEntity entity : list) {
                    if (entity.transfer_status == TransferStatus.WAITING) {
                        continue;
                    }

                    entity.transfer_status = TransferStatus.WAITING;
                    entity.transfer_result = TransferResult.NO_RESULT;
                    entity.transferred_size = 0;
                    entity.action_end_at = 0;

                    AppDatabase.getInstance().fileTransferDAO().update(entity);
                }

                emitter.onSuccess(true);
            }
        });
        addSingleDisposable(single, new Consumer<Boolean>() {
            @Override
            public void accept(Boolean aBoolean) throws Exception {
                if (consumer != null) {
                    consumer.accept(true);
                }
            }
        });

    }

    public void restartDownload(List<FileTransferEntity> list, Consumer<Boolean> consumer) {
        Single<Boolean> single = Single.create(new SingleOnSubscribe<Boolean>() {
            @Override
            public void subscribe(SingleEmitter<Boolean> emitter) throws Exception {

                for (FileTransferEntity entity : list) {
                    if (entity.transfer_status == TransferStatus.WAITING) {
                        continue;
                    }

                    if (entity.transfer_action == TransferAction.DOWNLOAD) {
                        FileUtils.delete(entity.target_path);
                        SLogs.d("deleted : " + entity.target_path);
                    }

                    entity.transfer_status = TransferStatus.WAITING;
                    entity.transfer_result = TransferResult.NO_RESULT;
                    entity.transferred_size = 0;
                    entity.action_end_at = 0;

                    AppDatabase.getInstance().fileTransferDAO().update(entity);
                }

                emitter.onSuccess(true);
            }
        });

        addSingleDisposable(single, new Consumer<Boolean>() {
            @Override
            public void accept(Boolean aBoolean) throws Exception {
                if (consumer != null) {
                    consumer.accept(true);
                }
            }
        });
    }
}
