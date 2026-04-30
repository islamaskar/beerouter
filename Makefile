.PHONY: image

IMAGE ?= beerouter

image:
	podman build . -t $(IMAGE)

.PHONY: codex
codex: image
	podman run --rm --cpus 4 --memory=16g -it \
		-v ~/.gitconfig:/root/.gitconfig:ro -v ~/.codex:/root/.codex \
		-v .:/root/src -w /root/src $(IMAGE) codex -s danger-full-access -a never

.PHONY: opencode
opencode: image
	podman run --rm --cpus 4 --memory=8g -it \
		-v $(HOME)/.gitconfig:/root/.gitconfig:ro,z \
		-v $(HOME)/.config/opencode:/root/.config/opencode:z \
		-v $(HOME)/.local/share/opencode/:/root/.local/share/opencode/:z \
		-v $(HOME)/.agents:/root/.agents:z \
		-v $(HOME)/.config/superpowers:/root/.config/superpowers:z \
		-v .:/root/src:z -w /root/src \
		--net host \
		$(IMAGE) opencode $(ARGS)
