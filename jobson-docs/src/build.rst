Building Jobson from Source
===========================



# Build

Building the Jobson jar for development requires `maven` and `jdk` (8+). For
example:

```bash
apt install maven openjdk-8-jdk

mvn package
```

Building an entire release (packages, documentation, etc.) additionally
requires `ruby` and `bundler`, along with some gems. For example:

```bash
apt install maven openjdk-8-jdk ruby ruby-bundler
gem install fpm

mvn package -P release
```

See `.travis.yml` for a "clean" build example.