package me.bottdev.meshdi.moduleit.api;

public sealed interface LeakDetectorResult {

    record Freed() implements LeakDetectorResult {}

    record Leaked(Throwable error) implements LeakDetectorResult {}

    record Disabled() implements LeakDetectorResult {}

}
