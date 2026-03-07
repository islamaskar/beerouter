FROM ghcr.io/catthehacker/ubuntu:act-24.04

RUN apt update && apt install default-jdk ripgrep -y

RUN curl -fsSL https://claude.ai/install.sh | bash && \
    npm install -g @just-every/code

ENV PATH="/root/.local/bin:${PATH}"
