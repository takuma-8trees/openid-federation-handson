# Third-party components and provenance

- Swagger UI / swagger-ui-dist 5.34.1: Apache License 2.0. https://github.com/swagger-api/swagger-ui/tree/v5.34.1 . Official npm distribution is downloaded at Docker build time and checked against a pinned SHA-512. LICENSE, NOTICE (when included), and bundled license files are retained in application resources. Swagger UI assets are served locally by Entity A.

- Nimbus OAuth 2.0 / OpenID Connect SDK 11.38.2: Apache License 2.0. https://connect2id.com/products/nimbus-oauth-openid-connect-sdk
- Nimbus JOSE + JWT and transitive dependencies are resolved by Maven. Their upstream license/notice files remain in their distributed JARs; the shaded application JAR is a build artifact, not committed here.
- JUnit Jupiter 5.11.4: test dependency, Eclipse Public License 2.0. Not included as an application dependency.
- Eclipse Temurin / Maven container images: upstream software and licenses apply. Images are built/pulled by the reader, not republished by this repository.

Design reference: user-supplied `ai-sdp-cross-ecosystem-trust-codex-crud-federation-trust.zip`, particularly `agentic-mesh/FEDERATION.md`, the offline material generator and Nimbus validation extension. This repository implements an independent CUI teaching application in `cui/`, and preserves an older browser prototype at the root. It does not redistribute the original project, its secrets, or its Keycloak extensions.

The normative reference is OpenID Federation 1.0 Final, not the behavior of a third-party implementation. This teaching subset is not a certification or a claim of full specification conformance.
