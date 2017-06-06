FROM rhldcmesboot711.na.rccl.com:5000/mw-java-base-poc

# Create app directory
RUN mkdir -p /usr/src/app

WORKDIR /usr/src/app

COPY ./target/guest-accounts-password-impl-1.0-SNAPSHOT /usr/src/app

CMD bash -C 'bin/guest-accounts-password-impl';'bash'
