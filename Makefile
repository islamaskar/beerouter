.PHONY: image refactor

image:
	podman build ./prompts -t claude

refactor: image
	podman run --rm -e IS_SANDBOX=1 -v ~/.claude:/root/.claude -v .:/root/src -w /root/src claude claude --dangerously-skip-permissions -p "execute the prompt at ./prompts/refactor.md"

.PHONY: claude
claude: image
	# SYS_ADMIN for profiling
	podman run --rm --cap-add=SYS_ADMIN --cpus 4 --memory=16g -it -e IS_SANDBOX=1 \
 		-v ~/.gitconfig:/root/.gitconfig:ro -v ~/.claude:/root/.claude -v ~/.claude.json:/root/.claude.json \
 		-v .:/root/src -w /root/src claude claude --dangerously-skip-permissions

.PHONY: coder
coder: image
	podman run --rm --cpus 4 --memory=16g -it \
		-v ~/.gitconfig:/root/.gitconfig:ro -v ~/.code:/root/.code \
		-v .:/root/src -w /root/src claude coder

.PHONY: t3
t3: image
	podman run --rm --cpus 4 --memory=16g -d \
		--device /dev/dri \
		-e WAYLAND_DISPLAY=$$WAYLAND_DISPLAY \
		-e XDG_RUNTIME_DIR=$$XDG_RUNTIME_DIR \
		-e DBUS_SESSION_BUS_ADDRESS=$$DBUS_SESSION_BUS_ADDRESS \
		-v "$$XDG_RUNTIME_DIR/$$WAYLAND_DISPLAY:$$XDG_RUNTIME_DIR/$$WAYLAND_DISPLAY" \
		-v /run/dbus:/run/dbus \
		-v /run/user/1000/bus:/run/user/1000/bus \
		-e DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1000/bus \
		-e GTK_THEME=Adwaita:dark \
		-v ~/.gitconfig:/root/.gitconfig:ro \
		-v ~/.codex:/root/.codex \
		-v .:/root/src -w /root/src claude \
		t3code --ozone-platform=wayland --no-sandbox --force-device-scale-factor=1.15
