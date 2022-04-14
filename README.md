# PDS Zenodo Adapter

Adapter to integrate with the [Public Data Space project](https://gitlab.fokus.fraunhofer.de/public-data-space).


## Build

Requirements:

* Git
* Maven 3
* Java 11

```bash
$ git clone <gitrepouri>
$ cd public-data-space-zenodo-adapter
$ mvn clean package
```

## Run

```bash
java -jar target/public-data-space-zenodo-adapter-1.1.0-fat.jar
```

## Docker

Build docker image:

```bash
docker build -t public-data-space-zenodo-adapter .
```

The next step requires that the other applications have been launched as described [here](https://gitlab.fokus.fraunhofer.de/public-data-space/public-data-space-connector).  
Run docker image:

```bash
docker run --network="ids_connector" -it --rm --name public-data-space-zenodo-adapter -p 8081:8080 public-data-space-zenodo-adapter
```

## Configuration
All keys ship with sane defaults so the application should run without custom configuration.

| Variable                   | Description                                       | Default Value                         |
| :------------------------- | :------------------------------------------------ | :------------------------------------ |
| `ENV_SQLITE_DB_NAME`       | SQLite database name.                             | `zenodo-adapter`                      |
| `ROUTE_ALIAS`              |                                                   | `public-data-space-zenodo-adapter`                  |
| `MANAGER_HOST`      | Config manager host.                              | `public-data-space-connector`                  |
| `CMANAGER_PORT`      | Config manager port.                              | `8080`                                |
