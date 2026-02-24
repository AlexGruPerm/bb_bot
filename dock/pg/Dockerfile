ARG PG_VERS=17
FROM postgres:${PG_VERS}

ARG PG_VERS=17

RUN echo "deb http://ftp.debian.org/debian bookworm main contrib non-free" > /etc/apt/sources.list.d/bookworm.list

RUN apt-get update  

RUN apt-get update \
    && apt-get install -y wget \
    && apt-get install -y unzip \
    && apt-get install -y vim \
    && apt-get install -y libevent-dev \
    && apt-get install -y git \
    && apt-get install -y libaio1 \
    && apt-get install -y iputils-ping \
    && apt-get install -y telnet \
    && apt-get install -y procps \
    && apt-get install -y build-essential \
    && apt-get install -y apt-utils \
    && apt-get install -y postgresql-server-dev-${PG_VERS} \
    && rm -rf /var/lib/apt/lists/*
    
RUN cd /tmp && rm -rf pg_cron && git clone https://github.com/citusdata/pg_cron.git && cd pg_cron && make clean && make && make install

VOLUME ["/var/lib/postgresql/data"]
