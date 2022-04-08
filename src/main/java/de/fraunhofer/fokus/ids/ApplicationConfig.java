package de.fraunhofer.fokus.ids;

public class ApplicationConfig {

    private static final String ROUTE_PREFIX = "de.fraunhofer.fokus.ids.";

    public static final String DATABASE_SERVICE = ROUTE_PREFIX + "databaseService";
    public static final String ZENODO_SERVICE = ROUTE_PREFIX + "zenodoService";

    public static final String ENV_SQLITE_DB_NAME = "SQLITE_DB_NAME";
    public static final String DEFAULT_SQLITE_DB_NAME = "zenodo-adapter";

    public static final String ENV_ROUTE_ALIAS = "ROUTE_ALIAS";
    public static final String DEFAULT_ROUTE_ALIAS = "localhost";
    
    public static final String DEFAULT_ADAPTER_NAME = "public-data-space-zenodo-adapter";
   
    public static final String ENV_MANAGER_HOST = "MANAGER_HOST";
    public static final String DEFAULT_MANAGER_HOST = "localhost";

    public static final String ENV_MANAGER_PORT = "MANAGER_PORT";
    public static final Integer DEFAULT_MANAGER_PORT = 8080;
    
    public static final Integer DEFAULT_ZENODO_PORT = 8070;
}