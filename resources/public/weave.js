// https://gist.githubusercontent.com/scwood/3bff42cc005cc20ab7ec98f0d8e1d59d/raw/32aaada5e4493efc8c728a571341821025190fa5/uuidV4.js
function uuidV4() {
    const uuid = new Array(36);
    for (let i = 0; i < 36; i++) {
	uuid[i] = Math.floor(Math.random() * 16);
    }
    uuid[14] = 4;
    uuid[19] = uuid[19] &= ~(1 << 2);
    uuid[19] = uuid[19] |= (1 << 3);
    uuid[8] = uuid[13] = uuid[18] = uuid[23] = '-';
    return uuid.map((x) => x.toString(16)).join('');
}

function getOrCreateInstanceId() {
    let tabId;

    if (window.opener && sessionStorage.getItem("weave-tab-id")) {
	tabId = uuidV4();
    } else {
	tabId = sessionStorage.getItem("weave-tab-id");
	if (!tabId) {
	    tabId = uuidV4();
	}
    }

    sessionStorage.setItem("weave-tab-id", tabId);
    return tabId;
}

window.weave = {
    setup: function(serverId, keepAlive = false) {
	window.weaveServerId = serverId
	window.weaveInstanceId = getOrCreateInstanceId()
	window.weaveKeepAlive = keepAlive

	tailwind.config = {darkMode: "class"}

	window.addEventListener('hashchange', function(e) {
            if (!window.__pushHashChange) {
		window.location.reload()
            }
	})
	window.__pushHashChange = false
    },

    csrf: function() {
	const match = document.cookie.match(/(^|)weave-csrf=([^;]+)/)
	return match ? match[2] : null
    },

    instance: function() {
	return window.weaveInstanceId
    },

    server: function() {
	return window.weaveServerId || ''
    },

    reload: function() {
	window.location.reload()
    },

    path: function() {
	const hashPath = window.location.hash.substring(1)
	if (!hashPath) {
            return "/"
	}
	let normalizedPath = hashPath.startsWith("/") ? hashPath : "/" + hashPath

	if (normalizedPath !== "/" && normalizedPath.endsWith("/")) {
	    normalizedPath = normalizedPath.slice(0, -1)
	    window.__pushHashChange = true
	    history.replaceState(null, null, "#" + normalizedPath)
	    window.__pushHashChange = false
	}
	return normalizedPath
    },

    pushHistoryState: function(url) {
	window.__pushHashChange = true

	let normalizedUrl = url
	if (normalizedUrl !== "/" && normalizedUrl.endsWith("/")) {
	    normalizedUrl = normalizedUrl.slice(0, -1)
	}
	history.pushState(null, null, "#" + normalizedUrl)
	window.__pushHashChange = false
    }
}

import('./datastar@v1.0.0-RC.1.js').then(({ load, apply }) => {
    // Define and load the CallAction
    const CallAction = {
	type: 'action',
	name: 'call',
	fn: async (ctx, url, options = {}) => {
            const callWithData = {}
            const prefix = 'callWith'

            // Find closest element with data-call-with-* attributes
            let currentEl = ctx.el
            while (currentEl) {
		let foundCallWith = false

		for (const [key, value] of Object.entries(currentEl.dataset)) {
		    if (key.startsWith(prefix)) {
			foundCallWith = true
			const paramName = key.slice(prefix.length)
			if (!(paramName in callWithData)) {
			    callWithData[paramName] = value
			}
		    }
		}

		if (foundCallWith) break
		currentEl = currentEl.parentElement
            }

            // Always add weave headers to options
            const enhancedOptions = {
                ...options,
                headers: {
		    'x-server-id': window.weave.server(),
                    'x-csrf-token': window.weave.csrf(),
                    'x-instance-id': window.weave.instance(),
                    'x-app-path': window.weave.path(),
                    ...(options.headers || {})
                }
            }

            // If no call-with data found, just use normal post with enhanced options
            if (Object.keys(callWithData).length === 0) {
		const postAction = ctx.actions.post
		return postAction.fn(ctx, url, enhancedOptions)
            }

            // Create a custom filtered function that includes our call-with data
            const originalFiltered = ctx.filtered
            const enhancedFiltered = (filterOptions) => {
		const signals = originalFiltered(filterOptions)
		return { ...signals, ...callWithData }
            }

            // Create enhanced context with our custom filtered function
            const enhancedCtx = {
		...ctx,
		filtered: enhancedFiltered
            }

            // Call the original post action with enhanced context and options
            const postAction = ctx.actions.post
            return postAction.fn(enhancedCtx, url, enhancedOptions)
	}
    }

    load(CallAction)

    setTimeout(() => {
        const mainEl = document.getElementById('weave-main')
        if (mainEl) {
            const options = window.weaveKeepAlive ? '{openWhenHidden: true}' : '{}'
            mainEl.setAttribute('data-on-load', `@call('/app-loader', ${options})`)
        }
    }, 0)
})
