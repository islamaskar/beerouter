FROM ghcr.io/catthehacker/ubuntu:act-24.04

RUN apt update && apt install default-jdk ripgrep \
    libegl1 libgl1 libgles2 \
    libwayland-client0 libwayland-egl1 \
    -y

RUN curl -fsSL https://claude.ai/install.sh | bash && \
    npm install -g @just-every/code && \
    npm i -g @openai/codex && \
    npm install -g @google/gemini-cli

RUN curl -fsSL https://github.com/pingdotgg/t3code/releases/download/v0.0.4/T3-Code-0.0.4-x86_64.AppImage \
    -o /tmp/t3code.AppImage && \
    chmod +x /tmp/t3code.AppImage && \
    /tmp/t3code.AppImage --appimage-extract && \
    mv squashfs-root /opt/t3code && \
    ln -s /opt/t3code/t3-code-desktop /usr/local/bin/t3code && \
    rm /tmp/t3code.AppImage

ENV PATH="/root/.local/bin:${PATH}"
