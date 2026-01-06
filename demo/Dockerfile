# docker build -f Dockerfile -t nakkaya/app:latest .
# docker run --rm --name app -d -p 8080:8080 nakkaya/app:latest

FROM ghcr.io/graalvm/native-image-community:24-muslib AS build

COPY ./target/demo.jar /dist/
COPY ./reflect-config.json /dist/
RUN mkdir -p /dist/build_cache

RUN native-image \
    --initialize-at-build-time \
    --no-server \
    --enable-url-protocols=https \
    -H:ReflectionConfigurationFiles=/dist/reflect-config.json \
    --initialize-at-run-time=sun.font.SunFontManager \
    --initialize-at-run-time=sun.font.FontManagerNativeLibrary \
    --initialize-at-run-time=sun.font.StrikeCache \
    --initialize-at-run-time=sun.font.PhysicalStrike \
    --initialize-at-run-time=sun.font.FreetypeFontScaler \
    -H:TempDirectory=/build/build_cache \
    --enable-native-access=ALL-UNNAMED \
    --static --libc=musl \
    -H:+UnlockExperimentalVMOptions \
    -H:IncludeResources="public/.*" \
    -jar /dist/demo.jar \
    -H:Name=/dist/app


FROM gcr.io/distroless/static-debian12
EXPOSE 8080
COPY --from=build /dist/app /app
ENTRYPOINT ["/app"]
