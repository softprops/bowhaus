# bowhaus

jvm hausing for [bower](http://bower.io/) components.


## install

Bowhaus can be deployed as a standalone jar that can be built by executing

```bash
sbt assembly
```

In the repos base directory. This will generate a `bowhaus.jar` file in the `target` directory.

The server stores bower components in redis so you will need to provide the server with a few flags to inform it where to resolve the redis host,
among other things

```bash
java -jar bowhaus.jar -p <port> -r <redis_connection_string> -n <redis_key_prefix>
```

`-p` tells bowhaus which port to listen on as an http server ( defaults to `8080` )

`-r` tells bowhaus how to connect to redis ( defaults to the factory defaults for `redis-server` )

`-n` provides a namespace which is prefixed on all generated redis keys ( defaults to `testing`)

## usage

Bowhaus works a url shortener for bower components

### Register components

```bash
curl 'http://localhost:8080/packages' -d 'name=foo&url=git://bar.com/foo.git'
```

This will return a `201` ( created )  status. If a component with the same name exists a `409` ( conflict ) status is returned.

### Listing components

```bash
curl 'http://localhost:8080/packages'
``

This will list all of the registered components in json format

### Getting a single component

```bash
curl 'http://localhost:8080/packages/foo'
```

This will get the json representation of a named component


Doug Tangren (softprops) 2013 