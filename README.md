# LeaderMS - English

This is a fork of LeaderMS.

## quickstart (localhost)

Obtain a localhost v62 client and modify `127.0.0.1` to redirect to `localhost`
with a hex editor.

Run the servers with the default testing settings.

```bash
docker-compose up
```

```bash
docker-compose run --rm  --service-ports adminer
```

After exiting docker-compose, remove any dangling services:

```bash
docker-compose down
```

The data for the server is persisted inside of a
[volume](https://docs.docker.com/storage/volumes/). To inspect the contents:

```bash
docker volume inspect argonms-server_argonms-volume
```

To delete all persisted data:

```bash
docker-compose down -v
```
