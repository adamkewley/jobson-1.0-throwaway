FROM ubuntu:16.04

RUN apt update && apt install -y \
    nginx \
    openjdk-8-jre \
    supervisor

# Install jobson-nix as an opt dependency
COPY target/dependency/jobson-nix/. /usr
RUN chmod +x /usr/bin/jobson

# Install nginx config
COPY default.conf /etc/nginx/conf.d/default.conf
RUN rm -rf /etc/nginx/sites-enabled/default

# Setup server acct. + workspace area (home dir)
RUN groupadd -r jobson && useradd --no-log-init -r -g jobson jobson
RUN mkdir -p /home/jobson && chown jobson:jobson /home/jobson

# Create a jobson workspace in the jobson home dir
USER jobson
RUN  cd /home/jobson && jobson new --demo  # so a blank img boot shows *something*
USER root

EXPOSE 80

COPY supervisord.conf /etc/supervisord.conf
CMD ["supervisord", "--configuration", "/etc/supervisord.conf", "--nodaemon"]
