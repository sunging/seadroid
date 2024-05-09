package com.seafile.seadroid2.framework.datastore;

public class DataStoreKeys {
    public static final String SEPARATOR = "::::";

    //data store
    public static final String DS_REPO_DIR_MAPPING = "data_store_repo_dir_mapping";
    public static final String LATEST_ACCOUNT = "latest_account";

    public static final String ACCOUNT_CURRENT_OLD = "com.seafile.seadroid.account_name";
    public static final String KEY_CURRENT_ACCOUNT = "current_account_signature";
    public static final String ACCOUNT_QUOTA_NO_LIMIT_KEY = "key_account_quota_no_limit";

    /**
     * When the app version is upgraded to v3.0.0(or v3x), some data must be migrated,
     * such as. CameraUploadDBHelper/FolderBackupDBHelper.
     * This field is used to check if it has been migrated.
     * <p>
     * 0 no
     * 1 yes
     * <p/>
     */
    public static final String DATA_IS_MIGRATION = "data_is_migrated_when_app_version_is_v3x";

    public static final String KEY_DARK_MODE = "key_dark_mode";

}