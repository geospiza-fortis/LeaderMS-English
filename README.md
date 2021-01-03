# LeaderMS - English

This is a fork of LeaderMS.

## quickstart (localhost)

Find the external ip address of your docker host. If you are running on Windows,
use `ipconfig` to find the WSL interface.

Obtain a localhost v62 client and modify `127.0.0.1` to redirect to the external
ip address with a hex editor.

In `conf/world.properties`, set the `channel.net.interface` to the external ip
address.

Run the servers with the default testing settings.

```bash
docker-compose up
```

To create accounts and manage the database, use the adminer instance on
localhost:8080. To add GM privileges, set `gm` to 5 in the character table.

After exiting docker-compose, remove any dangling services:

```bash
docker-compose down
```

The data for the server is persisted inside of a
[volume](https://docs.docker.com/storage/volumes/). To inspect the contents:

```bash
# this may change depending on the name of the parent directory
docker volume inspect leaderms-english_leaderms-volume
```

To delete all persisted data:

```bash
docker-compose down -v
```

## formatting

The container will fail to build if the code is ill formatted. Use
[prettier](https://prettier.io/) to format all java and javscript code. Run the
following command to check for errors:

```bash
mvn spotless:check
```

Run this command to apply formatting automatically:

```bash
mvn spotless:apply
```

If you do not have maven installed on your machine, you may start up a new
container to mount your source.

```bash
docker-compose run --rm world bash
mvn spotless:apply
```

## debugging

This code is janky. To make significant changes, you will need to know how to
debug java code.

### `/usr/bin/env: ‘bash\r’: No such file or directory`

To fix this, we need to remove Windows line endings.

```bash
docker-compose run --rm world bash
apt install dos2unix
dos2unix bin/*
```

Then rebuild using `docker-compose build`.

### building code

To ensure that code is built every time you re-run the server:

```bash
# ensure the interface is down
docker-compose down
docker-compose build
docker-compose up
```

### logging statements

To change logging levels, modify `conf/logging.properties`. Use logging
functions in the Java code to get a sense of what is happening. For example, if
you wanted to see every opcode being processed, set the following line:

```properties
handling.MapleServerHandler.level = FINEST
```

In general, you may add the slf4j logger to a class and enable logging from the
logging properties file.

### using jdb

In the `docker-compose.yaml` file, set `DEBUG=true` for any of the processes you
want to debug. This adds the following flag:

```bash
-Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=y
```

Execute a shell in the same container as the process. For example, to debug the
login server:

```bash
container_id=$(docker ps | grep _channel | cut -d" " -f1)
docker exec -it $container_id bash
```

Launch the remote debugging session in jdb:

```bash
jdb -attach localhost:8000 -sourcepath src
```

Then set breakpoints and run:

```bash
stop in handling.channel.handler.PlayerLoggedinHandler.handlePacket
run
```

There are some hooks for JMX debugging sprinkled about, but they haven't been
configured. Good luck on that.

See [this PR](https://github.com/geospiza-fortis/LeaderMS-English/pull/1) for
more details.
