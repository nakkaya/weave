window.initDarkMode = function() {
    const savedTheme = localStorage.getItem('theme');

    if (savedTheme) {
        if (savedTheme === 'dark') {
            document.documentElement.classList.add('dark');
        } else {
            document.documentElement.classList.remove('dark');
        }
    } else {
        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            document.documentElement.classList.add('dark');
            localStorage.setItem('theme', 'dark');
        } else {
            document.documentElement.classList.remove('dark');
            localStorage.setItem('theme', 'light');
        }
    }
}

window.toggleDarkMode = function() {
    const isDark = document.documentElement.classList.toggle('dark');
    localStorage.setItem('theme', isDark ? 'dark' : 'light');
}

window.positionDropdown = function(button) {
    const container = button.closest('.relative');
    if (!container) return;

    const dropdown = container.querySelector('.dropdown-menu');
    if (!dropdown) return;

    const rect = button.getBoundingClientRect();

    const rightPosition = window.innerWidth - rect.right;
    const topPosition = rect.bottom;

    dropdown.style.right = rightPosition + 'px';
    dropdown.style.top = topPosition + 'px';
    dropdown.style.left = 'auto';
}

window.toggleDropdown = function(button) {
    const container = button.closest('.dropdown-container');
    if (!container) return;

    const dropdown = container.querySelector('.dropdown-menu');
    const chevron = container.querySelector('.dropdown-chevron');
    if (!dropdown) return;

    // Close all other dropdowns first
    document.querySelectorAll('.dropdown-menu').forEach(menu => {
        if (menu !== dropdown && !menu.classList.contains('hidden')) {
            menu.classList.add('hidden');
            const otherChevron = menu.closest('.dropdown-container')?.querySelector('.dropdown-chevron');
            if (otherChevron) otherChevron.classList.remove('rotate-180');
        }
    });

    // Toggle this dropdown
    const isHidden = dropdown.classList.contains('hidden');
    if (isHidden) {
        dropdown.classList.remove('hidden');
        if (chevron) chevron.classList.add('rotate-180');
        // Position if needed (for fixed positioning)
        if (dropdown.classList.contains('fixed')) {
            positionDropdown(button);
        }
    } else {
        dropdown.classList.add('hidden');
        if (chevron) chevron.classList.remove('rotate-180');
    }
}

document.addEventListener('DOMContentLoaded', function() {
    // Close dropdown when clicking an item
    document.addEventListener('click', function(e) {
        const dropdownItem = e.target.closest('.dropdown-menu li');
        if (dropdownItem) {
            const container = dropdownItem.closest('.dropdown-container');
            if (container) {
                const dropdown = container.querySelector('.dropdown-menu');
                const chevron = container.querySelector('.dropdown-chevron');
                if (dropdown) dropdown.classList.add('hidden');
                if (chevron) chevron.classList.remove('rotate-180');
            }
        }

        // Close dropdowns when clicking outside
        if (!e.target.closest('.dropdown-container')) {
            document.querySelectorAll('.dropdown-menu').forEach(menu => {
                if (!menu.classList.contains('hidden')) {
                    menu.classList.add('hidden');
                    const chevron = menu.closest('.dropdown-container')?.querySelector('.dropdown-chevron');
                    if (chevron) chevron.classList.remove('rotate-180');
                }
            });
        }
    });
});

tailwind.config = {darkMode: "class"};
window.initDarkMode();

if (window.matchMedia) {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
        // Only auto-update if user hasn't explicitly set a preference
        if (!localStorage.getItem('theme')) {
            if (e.matches) {
                document.documentElement.classList.add('dark');
            } else {
                document.documentElement.classList.remove('dark');
            }
        }
    });
}

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
    setup: function(serverId, keepAlive = false, devMode = false) {
	window.weaveServerId = serverId
	window.weaveInstanceId = getOrCreateInstanceId()
	window.weaveKeepAlive = keepAlive
	window.weaveDevMode = devMode

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
	let appPath = window.location.hash.substring(1)

	if (!appPath) {
            return "/"
	}

	appPath = appPath.split('?')[0]

	appPath = appPath.startsWith("/") ? appPath : "/" + appPath

	if (appPath !== "/" && appPath.endsWith("/")) {
	    appPath = appPath.slice(0, -1)
	    window.__pushHashChange = true
	    history.replaceState(null, null, "#" + appPath)
	    window.__pushHashChange = false
	}
	return appPath
    },

    queryParams: function() {
	const hash = window.location.hash.substring(1)
	const queryIndex = hash.indexOf('?')

	if (queryIndex === -1) {
	    return window.location.search || ""
	}

	return '?' + hash.substring(queryIndex + 1)
    },

    pushHistoryState: function(url) {
	window.__pushHashChange = true

	let appPath = url
	if (appPath !== "/" && appPath.endsWith("/")) {
	    appPath = appPath.slice(0, -1)
	}
	history.pushState(null, null, "#" + appPath)
	window.__pushHashChange = false
    }
}

import('./datastar@v1.0.0-RC.4.js').then(({ load, apply }) => {
    // Monkey patch fetch to dynamically add app path and query params header on every d* request
    const originalFetch = window.fetch;
    window.fetch = (input, init) => {
        if (init?.headers?.['Datastar-Request']) {
            const updatedHeaders = {
                ...init.headers,
                'x-app-path': window.weave.path(),
                'x-query-params': window.weave.queryParams()
            };
            return originalFetch(input, { ...init, headers: updatedHeaders });
        }
        return originalFetch(input, init);
    };

    // Global store for active requests per route to prevent duplicate
    // requests in serialize mode
    const activeRouteRequests = new Map()

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

            // Handle different request cancellation modes
            const requestCancellation = options?.requestCancellation ?? 'auto'

            if (requestCancellation === 'serialize') {
                // Check if there's already an active request for this route
                if (activeRouteRequests.has(url)) {
                    return Promise.resolve()
                }

                // Mark this route as having an active request
                activeRouteRequests.set(url, true)
            }

            const enhancedOptions = {
                ...options,
                headers: {
		    'x-server-id': window.weave.server(),
                    'x-csrf-token': window.weave.csrf(),
                    'x-instance-id': window.weave.instance(),
                    'x-timezone': Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
                    'x-language': navigator.language || 'en',
		    'X-Accel-Buffering': 'no',
                    ...(options.headers || {})
                }
            }

            try {
                // If no call-with data found, just use normal post with enhanced options
                if (Object.keys(callWithData).length === 0) {
		    const postAction = ctx.actions.post
		    return await postAction.fn(ctx, url, enhancedOptions)
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
                return await postAction.fn(enhancedCtx, url, enhancedOptions)
            } finally {
                // Clean up the route lock when request completes (success or error)
                if (requestCancellation === 'serialize') {
                    activeRouteRequests.delete(url)
                }
            }
	}
    }

    load(CallAction)

    setTimeout(() => {
        const mainEl = document.getElementById('weave-main')
        if (mainEl) {
            const options = window.weaveKeepAlive ? '{openWhenHidden: true}' : '{}'
            mainEl.setAttribute('data-on-load', `@call('/app-loader', ${options})`)
        }

	if (window.weaveDevMode) {
	    document.addEventListener('datastar-signal-patch', (event) => {
		console.log('signal change:', event.detail)
	    })
	}
    }, 0)
})
