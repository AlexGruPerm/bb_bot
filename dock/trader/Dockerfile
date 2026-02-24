FROM openjdk:26-ea-6-jdk-slim-bookworm

RUN apt-get update && apt-get install -y \
    vim \
    telnet \
    procps \
    iputils-ping \
    telnet \
    && rm -rf /var/lib/apt/lists/*
    
WORKDIR /app

COPY logback.xml /app/logback.xml
COPY bb_bot.pem /app/bb_bot.pem
COPY bb_bot.p12 /app/bb_bot.p12
COPY controlt.conf /app/controlt.conf
COPY trade_bot.jar /app/trade_bot.jar
COPY start.sh /app/

CMD ["sh", "./start.sh"]

