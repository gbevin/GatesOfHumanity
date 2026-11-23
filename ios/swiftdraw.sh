# Sourced from the top of the repository by the scripts that build SwiftDraw from its sources:
# ios/build.sh, ios/build-watch.sh and mac/build.sh. It sets $swiftdraw to the sources of the
# version that ios/project.yml names, and fetches them the first time.
swiftdraw_version=$(sed -n '/^  SwiftDraw:/,/exactVersion/s/.*exactVersion: *//p' ios/project.yml)
swiftdraw="ios/build/SwiftDraw-$swiftdraw_version"
if [ ! -d "$swiftdraw" ]; then
    mkdir -p ios/build
    git clone --depth 1 --branch "$swiftdraw_version" -q https://github.com/swhitty/SwiftDraw.git "$swiftdraw"
fi
