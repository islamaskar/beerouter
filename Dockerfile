FROM ghcr.io/catthehacker/ubuntu:act-24.04

ENV OPENCODE_EXPERIMENTAL_DISABLE_COPY_ON_SELECT=true

RUN apt update && apt upgrade -y && apt install default-jdk ripgrep python3-pip \
    libegl1 libgl1 libgles2 \
    libwayland-client0 libwayland-egl1 \
    -y

RUN curl -fsSL https://claude.ai/install.sh | bash && \
    python3 -m pip install --break-system-packages desloppify && \
    npm install -g @openai/codex opencode-ai

ENV PATH="/root/.local/bin:${PATH}"
