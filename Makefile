.PHONY: image

IMAGE ?= beerouter

IMAGE ?= beerouter


image:
	podman build . -t $(IMAGE)

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
