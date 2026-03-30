FROM ghcr.io/catthehacker/ubuntu:act-24.04

RUN apt update && apt install default-jdk ripgrep python3-pip \
    libegl1 libgl1 libgles2 \
    libwayland-client0 libwayland-egl1 \
    -y

RUN curl -fsSL https://claude.ai/install.sh | bash && \
    python3 -m pip install --break-system-packages desloppify && \
    npm install -g @openai/codex

ENV PATH="/root/.local/bin:${PATH}"
