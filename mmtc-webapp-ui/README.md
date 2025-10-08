# mmtc-webapp-ui

Stack:
- Vue 3
- Nuxt 3
- NuxtUI (3)

## Initial setup notes:

Followed https://nuxt.com/docs/3.x/getting-started/installation.

1. Installed [Node](https://nodejs.org/en/download)
    1. As of the time of writing this, I installed node v22.19.0 and npm 10.9.3 
2. `npm create nuxt@latest mmtc-webapp-ui`
   3. Selected: npm for package manager, and installed @nuxt/ui and @nuxt/test-utils

Latest, ran:

```
# Download and install nvm:
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash

# in lieu of restarting the shell
\. "$HOME/.nvm/nvm.sh"

# Download and install Node.js:
nvm install 22

# Verify the Node.js version:
node -v # Should print "v22.20.0".

# Verify npm version:
npm -v # Should print "10.9.3".


```

## Building

```
# first time only:
npm install

# to regenerate:
npx nuxt generate
```