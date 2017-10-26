FROM openjdk:8-jre-alpine

RUN apk add --update bash && rm -rf /var/cache/apk/*
RUN apk add --update tcpdump && rm -rf /var/cache/apk/*

ENV APP_DIRECTORY /usr/src/app
ENV APP_LIB_DIR $APP_DIRECTORY/lib/

RUN echo "Creating APP Directory $APP_DIRECTORY"
RUN mkdir -p $APP_DIRECTORY

RUN echo "Creating APP Library Directory $APP_LIB_DIR"
RUN mkdir -p $APP_LIB_DIR

RUN echo "Copying jars to  APP Library Directory $APP_LIB_DIR"
COPY ./maven/* $APP_LIB_DIR

RUN echo "Adding entryPoint.sh"
ADD ./maven/bin/entryPoint.sh /entryPoint.sh

ENTRYPOINT ["/entryPoint.sh","$APP_DIRECTORY","$APP_LIB_DIR"]
