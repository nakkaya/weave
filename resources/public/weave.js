  import { load, apply } from './datastar@v1.0.0-RC.1.js'

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
