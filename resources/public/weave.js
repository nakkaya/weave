window.weave = {
    setup: function(serverId, instanceId) {
	window.weaveServerId = serverId
	window.weaveInstanceId = instanceId

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
	return hashPath.startsWith("/") ? hashPath : "/" + hashPath
    },

    pushHistoryState: function(url) {
	window.__pushHashChange = true
	history.pushState(null, null, "#" + url)
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
			const paramName = key.slice(prefix.length).toLowerCase()
			if (!(paramName in callWithData)) {
			    callWithData[paramName] = value
			}
		    }
		}

		if (foundCallWith) break
		currentEl = currentEl.parentElement
            }

            // If no call-with data found, just use normal post
            if (Object.keys(callWithData).length === 0) {
		const postAction = ctx.actions.post
		return postAction.fn(ctx, url, options)
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

            // Call the original post action with enhanced context
            const postAction = ctx.actions.post
            return postAction.fn(enhancedCtx, url, options)
	}
    }
    
    load(CallAction)
})
