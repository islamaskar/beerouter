.PHONY: image refactor

image:
	podman build ./prompts -t claude

refactor: image
	podman run --rm -e IS_SANDBOX=1 -v ~/.claude:/root/.claude -v .:/root/src -w /root/src claude claude --dangerously-skip-permissions -p "execute the prompt at ./prompts/refactor.md"
