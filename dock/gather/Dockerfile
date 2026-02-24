FROM openjdk:26-ea-6-jdk-slim-bookworm

RUN apt-get update && apt-get install -y \
    vim \
    telnet \
    procps \
    iputils-ping \
    telnet \
    && rm -rf /var/lib/apt/lists/*
    
WORKDIR /app
COPY control.conf /app/control.conf
COPY gather.jar /app/gather.jar
COPY start.sh /app/

CMD ["sh", "./start.sh"]

