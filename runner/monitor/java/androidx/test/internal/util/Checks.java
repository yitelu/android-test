/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.test.internal.util;

import android.os.Looper;
import androidx.annotation.RestrictTo;
import androidx.test.internal.platform.ServiceLoaderWrapper;
import androidx.test.internal.platform.ThreadChecker;

/**
 * Utility methods for checking null references, method arguments, and thread state to simplify
 * other library code.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // used by runner and rules
public final class Checks {

  private Checks() {}

  public static <T> T checkNotNull(T reference) {
    if (reference == null) {
      throw new NullPointerException();
    }
    return reference;
  }

  public static <T> T checkNotNull(T reference, Object errorMessage) {
    if (reference == null) {
      throw new NullPointerException(String.valueOf(errorMessage));
    }
    return reference;
  }

  public static <T> T checkNotNull(
      T reference, String errorMessageTemplate, Object... errorMessageArgs) {
    if (reference == null) {
      // If either of these parameters is null, the right thing happens
      // anyway
      throw new NullPointerException(format(errorMessageTemplate, errorMessageArgs));
    }
    return reference;
  }

  /** Throw an {@link IllegalArgumentException} when {@code expression} is false. */
  public static void checkArgument(boolean expression) {
    if (!expression) {
      throw new IllegalArgumentException();
    }
  }

  /**
   * Throw an {@link IllegalArgumentException} when {@code expression} is false with the specified
   * {@code errorMessage}.
   */
  public static void checkArgument(boolean expression, Object errorMessage) {
    if (!expression) {
      throw new IllegalArgumentException(String.valueOf(errorMessage));
    }
  }

  /**
   * Throw an {@link IllegalArgumentException} when {@code expression} is false with the message
   * formatted by {@code errorMessageTemplate} and {@code errorMessageArgs}.
   */
  public static void checkArgument(
      boolean expression, String errorMessageTemplate, Object... errorMessageArgs) {
    if (!expression) {
      throw new IllegalArgumentException(format(errorMessageTemplate, errorMessageArgs));
    }
  }

  public static void checkState(boolean expression) {
    if (!expression) {
      throw new IllegalStateException();
    }
  }

  /**
   * Throw an {@link IllegalStateException} when {@code expression} is false with the specified
   * {@code errorMessage}.
   */
  public static void checkState(boolean expression, Object errorMessage) {
    if (!expression) {
      throw new IllegalStateException(String.valueOf(errorMessage));
    }
  }

  /**
   * Throw an {@link IllegalStateException} when {@code expression} is false with the specified
   * {@code errorMessage}.
   */
  public static void checkState(
      boolean expression, String errorMessageTemplate, Object... errorMessageArgs) {
    if (!expression) {
      throw new IllegalStateException(format(errorMessageTemplate, errorMessageArgs));
    }
  }

  /**
   * Checks if the current thread is the main thread, otherwise throws.
   *
   * @throws IllegalStateException if current thread is not the main thread.
   */
  public static void checkMainThread() {
    ThreadCheckerSingleton.INSTANCE.checkMainThread();
  }

  /**
   * Checks if the current thread is not the main thread, otherwise throws.
   *
   * @throws IllegalStateException if current thread is the main thread.
   */
  public static void checkNotMainThread() {
    ThreadCheckerSingleton.INSTANCE.checkNotMainThread();
  }

  /**
   * Lazy load the ThreadChecker singleton using initialization-on-demand holder idiom
   * https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom
   */
  private static class ThreadCheckerSingleton {

    private static final ThreadChecker INSTANCE =
        ServiceLoaderWrapper.loadSingleService(
            ThreadChecker.class,
            () ->
                new ThreadChecker() {
                  @Override
                  public void checkMainThread() {
                    checkState(
                        Thread.currentThread().equals(Looper.getMainLooper().getThread()),
                        "Method cannot be called off the main application thread (on: %s)",
                        Thread.currentThread().getName());
                  }

                  @Override
                  public void checkNotMainThread() {
                    checkState(
                        !Thread.currentThread().equals(Looper.getMainLooper().getThread()),
                        "Method cannot be called on the main application thread (on: %s)",
                        Thread.currentThread().getName());
                  }
                });
  }

  private static String format(String template, Object... args) {
    template = String.valueOf(template); // null -> "null"

    // start substituting the arguments into the '%s' placeholders
    StringBuilder builder = new StringBuilder(template.length() + 16 * args.length);
    int templateStart = 0;
    int i = 0;
    while (i < args.length) {
      int placeholderStart = template.indexOf("%s", templateStart);
      if (placeholderStart == -1) {
        break;
      }
      builder.append(template.substring(templateStart, placeholderStart));
      builder.append(args[i++]);
      templateStart = placeholderStart + 2;
    }
    builder.append(template.substring(templateStart));

    // if we run out of placeholders, append the extra args in square braces
    if (i < args.length) {
      builder.append(" [");
      builder.append(args[i++]);
      while (i < args.length) {
        builder.append(", ");
        builder.append(args[i++]);
      }
      builder.append(']');
    }
    return builder.toString();
  }
}
