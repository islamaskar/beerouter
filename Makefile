.PHONY: image

IMAGE ?= beerouter


image:
	podman build . -t $(IMAGE)

.PHONY: claude
claude: image
	# SYS_ADMIN for profiling
	podman run --rm --cap-add=SYS_ADMIN --cpus 4 --memory=16g -it -e IS_SANDBOX=1 \
 		-v ~/.gitconfig:/root/.gitconfig:ro -v ~/.claude:/root/.claude -v ~/.claude.json:/root/.claude.json \
 		-v .:/root/src -w /root/src $(IMAGE) claude --dangerously-skip-permissions

.PHONY: codex
codex: image
	podman run --rm --cpus 4 --memory=16g -it \
		-v ~/.gitconfig:/root/.gitconfig:ro -v ~/.codex:/root/.codex \
		-v .:/root/src -w /root/src $(IMAGE) codex -s danger-full-access -a never
