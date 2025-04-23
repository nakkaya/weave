# docker build -f Dockerfile -t nakkaya/app:latest .
# docker run --rm --name app -d -p 8080:8080 nakkaya/app:latest

FROM ghcr.io/graalvm/native-image-community:24-muslib AS build

RUN curl -O https://download.clojure.org/install/linux-install-1.11.1.1237.sh && \
    chmod +x linux-install-1.11.1.1237.sh && \
    ./linux-install-1.11.1.1237.sh

COPY . /build
WORKDIR /build
RUN clojure -X:uberjar
RUN mkdir -p /build/build_cache
RUN native-image \
    --initialize-at-build-time \
    --no-server \
    -H:ReflectionConfigurationFiles=reflect-config.json \
    -H:TempDirectory=/build/build_cache \
    --enable-native-access=ALL-UNNAMED \
    --static --libc=musl \
    -H:+UnlockExperimentalVMOptions \
    -H:IncludeResources="public/.*" \
    -jar ./app.jar \
    -H:Name=./app.app


FROM gcr.io/distroless/base
EXPOSE 8080
COPY --from=build /build/app /app
ENTRYPOINT ["/app"]
