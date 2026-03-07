.PHONY: image refactor

image:
	podman build ./prompts -t claude

refactor: image
	podman run --rm -e IS_SANDBOX=1 -v ~/.claude:/root/.claude -v .:/root/src -w /root/src claude claude --dangerously-skip-permissions -p "execute the prompt at ./prompts/refactor.md"

.PHONY: claude
claude: image
	# SYS_ADMIN for profiling
	podman run --rm --cap-add=SYS_ADMIN --cpus 4 -it -e IS_SANDBOX=1 \
 		-v ~/.claude:/root/.claude -v ~/.claude.json:/root/.claude.json \
 		-v .:/root/src -w /root/src claude claude --dangerously-skip-permissions

.PHONY: coder
coder: image
	podman run --rm --cpus 4 -it \
		-v ~/.code:/root/.code \
		-v .:/root/src -w /root/src claude coder
