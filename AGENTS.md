### Agent Guidelines

1. **Never hardcode environment-specific properties in code.**  
   - Do not bake default server URLs, API tokens, or marker lists inside Java classes (e.g., avoid `new HttpClient` consumers setting base URLs directly).  
   - All configurable values must come from Spring configuration (`application.yml`, profiles, or `@ConfigurationProperties`).  
   - If a property is required, fail fast with a descriptive message instead of silently choosing a fallback.

2. **Reuse shared infrastructure beans.**  
   - Use the shared `HttpClient` provided by `JellyfinHttpClientConfiguration` instead of instantiating new clients in individual services.  
   - If you need a new shared component, declare it as a bean and inject it where necessary.

3. **Keep test resources aligned.**  
   - When adding or changing required properties, remember to update `src/test/resources/application.yml` so Spring Boot tests bootstrap cleanly.

4. **Document configuration requirements.**  
   - When introducing new properties, update this file and `application.yml` with clear defaults or guidance so future agents know what must be set.
