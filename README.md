# fleet-buddy
Eve Online fleet buddy based on the CREST API

# Development
```bash
cp ./server/src/linux/etc/fleetbuddy/application.conf.sample application.conf
```

And fill in the values, according to `server/src/debian/DEBIAN/postinst`
initConfigFile. Don't forget to run the postgresql commands too.

# semantic ui setup

Currently custom fork for horizontal list fix, replace `npm install` with `git clone git@github.com:reactormonk/Semantic-UI.git semantic`

```bash
cd client
npm install semantic-ui --save
cd semantic
../node_modules/.bin/gulp build
```

# elm / css setup

Either install via `-g` or do some `PATH` magic so sbt finds the binaries.

```bash
cd client
npm install elm
npm install elm-css
```

# Production install
```bash
sbt debian:packageBin
```

Configure ssl via let's encrypt, with a forwarder of your choice. The
application runs on port `9476` by default, which can be changed via `port =
6402` in the `application.conf`.
