# Exception Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace untyped and misused exceptions with a typed hierarchy, fix catch-and-rethrow patterns, narrow broad catches, and add `@throws` KDoc to all public API methods.

**Architecture:** Define 5 new typed exceptions in a new `exceptions` subpackage under `router/`. Replace `RuntimeException` throws with the correct typed exception. Replace misused `IllegalArgumentException` (routing failures) with `RoutingException`. Fix catch-and-rethrow to preserve original types. Narrow broad `catch (e: Exception)` blocks. Add `@throws` annotations to public API.

**Tech Stack:** Kotlin, kotlin.test (`assertFailsWith`), kotlinx-benchmark (existing), Gradle

---

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/exceptions/RoutingException.kt` | Base routing failure |
| Create | `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/exceptions/DataCorruptionException.kt` | Data integrity invariant violations |
| Create | `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/exceptions/ExpressionParseException.kt` | Profile expression parse failures |
| Create | `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/exceptions/CacheStateException.kt` | Internal state invariant violations |
| Create | `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/exceptions/DataFileNotFoundException.kt` | File I/O errors in data loading |
| Modify | `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingIslandException.kt` | Add message/cause constructors, extend RoutingException |
| Modify | `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt` | Replace IAE with RoutingException, dirtyMessage pattern fix, @throws |
| Modify | `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt` | Replace RuntimeException in setModel, narrow nogo catch, @throws |
| Modify | `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContext.kt` | Replace RuntimeException, narrow catches, fix catch-and-rethrow |
| Modify | `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpression.kt` | Chain cause in NumberFormatException catch |
| Modify | `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/TagValueCoder.kt` | Replace RuntimeException with DataCorruptionException |
| Modify | `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/MicroCache2.kt` | Replace RuntimeException with CacheStateException |
| Modify | `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/MicroCache.kt` | Replace IllegalArgumentException with UnsupportedOperationException for base class stubs |
| Modify | `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/NodesCache.kt` | Replace RuntimeException wrapper with DataFileNotFoundException |
| Modify | `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/PhysicalFile.kt` | Narrow catch in checkVersionIntegrity |
| Modify | `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/Rd5DiffValidator.kt` | Replace RuntimeException with DataCorruptionException |
| Modify | `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/Rd5DiffTool.kt` | Replace RuntimeException with DataCorruptionException |
| Modify | `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/IntegerFifo3Pass.kt` | No change needed — IndexOutOfBoundsException is appropriate |
| Modify | `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AreaReader.kt` | Narrow broad catches |
| Create | `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/exceptions/RoutingExceptionTest.kt` | Test all new exceptions |
| Create | `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/exceptions/DataCorruptionExceptionTest.kt` | Test DataCorruptionException |
| Create | `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/exceptions/ExpressionParseExceptionTest.kt` | Test ExpressionParseException |
| Create | `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/exceptions/CacheStateExceptionTest.kt` | Test CacheStateException |
| Create | `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/exceptions/DataFileNotFoundExceptionTest.kt` | Test DataFileNotFoundException |

---

### Task 1: Create exception hierarchy

**Files:**
- Create: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/exceptions/RoutingException.kt`
- Create: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/exceptions/DataCorruptionException.kt`
- Create: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/exceptions/ExpressionParseException.kt`
- Create: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/exceptions/CacheStateException.kt`
- Create: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/exceptions/DataFileNotFoundException.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingIslandException.kt`

- [ ] **Step 1: Create `RoutingException.kt`**

```kotlin
package dev.skynomads.beerouter.router.exceptions

public open class RoutingException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
```

- [ ] **Step 2: Create `DataCorruptionException.kt`**

```kotlin
package dev.skynomads.beerouter.router.exceptions

public class DataCorruptionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
```

- [ ] **Step 3: Create `ExpressionParseException.kt`**

```kotlin
package dev.skynomads.beerouter.router.exceptions

public class ExpressionParseException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
```

- [ ] **Step 4: Create `CacheStateException.kt`**

```kotlin
package dev.skynomads.beerouter.router.exceptions.exceptions

public class CacheStateException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
```

Wait — that package is wrong. Let me fix:

```kotlin
package dev.skynomads.beerouter.router.exceptions

public class CacheStateException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
```

- [ ] **Step 5: Create `DataFileNotFoundException.kt`**

```kotlin
package dev.skynomads.beerouter.router.exceptions

public class DataFileNotFoundException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
```

- [ ] **Step 6: Update `RoutingIslandException.kt` to extend `RoutingException` and add constructors**

```kotlin
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.router.exceptions.RoutingException

public class RoutingIslandException(
    message: String = "routing island detected",
    cause: Throwable? = null,
) : RoutingException(message, cause)
```

- [ ] **Step 7: Run the build to verify compilation**

Run: `./gradlew :core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/exceptions/ core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingIslandException.kt
git commit -m "feat(core): add typed exception hierarchy for routing engine

Add RoutingException, DataCorruptionException, ExpressionParseException,
CacheStateException, and DataFileNotFoundException. Update
RoutingIslandException to extend RoutingException with message/cause.

Co-Authored-By: opencode <opencode@opencode.ai>"
```

---

### Task 2: Write tests for new exception types

**Files:**
- Create: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/exceptions/RoutingExceptionTest.kt`
- Create: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/exceptions/DataCorruptionExceptionTest.kt`
- Create: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/exceptions/ExpressionParseExceptionTest.kt`
- Create: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/exceptions/CacheStateExceptionTest.kt`
- Create: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/exceptions/DataFileNotFoundExceptionTest.kt`

- [ ] **Step 1: Create `RoutingExceptionTest.kt`**

```kotlin
package dev.skynomads.beerouter.router.exceptions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RoutingExceptionTest {
    @Test
    fun `RoutingException is a RuntimeException`() {
        val ex = RoutingException("test")
        assertIs<RuntimeException>(ex)
    }

    @Test
    fun `RoutingException preserves message`() {
        val ex = RoutingException("routing failed")
        assertEquals("routing failed", ex.message)
    }

    @Test
    fun `RoutingException preserves cause`() {
        val cause = IllegalStateException("root")
        val ex = RoutingException("routing failed", cause)
        assertEquals("routing failed", ex.message)
        assertEquals(cause, ex.cause)
    }

    @Test
    fun `RoutingException cause defaults to null`() {
        val ex = RoutingException("test")
        assertNull(ex.cause)
    }

    @Test
    fun `RoutingIslandException extends RoutingException`() {
        val ex = RoutingIslandException()
        assertIs<RoutingException>(ex)
    }

    @Test
    fun `RoutingIslandException has default message`() {
        val ex = RoutingIslandException()
        assertEquals("routing island detected", ex.message)
    }

    @Test
    fun `RoutingIslandException accepts custom message and cause`() {
        val cause = RuntimeException("root")
        val ex = RoutingIslandException("custom island", cause)
        assertEquals("custom island", ex.message)
        assertEquals(cause, ex.cause)
    }
}
```

- [ ] **Step 2: Create `DataCorruptionExceptionTest.kt`**

```kotlin
package dev.skynomads.beerouter.router.exceptions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DataCorruptionExceptionTest {
    @Test
    fun `DataCorruptionException is a RuntimeException`() {
        val ex = DataCorruptionException("corrupt")
        assertIs<RuntimeException>(ex)
    }

    @Test
    fun `DataCorruptionException preserves message and cause`() {
        val cause = IOException("io error")
        val ex = DataCorruptionException("identity corruption!", cause)
        assertEquals("identity corruption!", ex.message)
        assertEquals(cause, ex.cause)
    }
}
```

Wait — `IOException` is not available in commonMain. Let me use `IllegalStateException` instead since these tests live in jvmTest but the exceptions are in commonMain.

```kotlin
package dev.skynomads.beerouter.router.exceptions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DataCorruptionExceptionTest {
    @Test
    fun `DataCorruptionException is a RuntimeException`() {
        val ex = DataCorruptionException("corrupt")
        assertIs<RuntimeException>(ex)
    }

    @Test
    fun `DataCorruptionException preserves message and cause`() {
        val cause = IllegalStateException("root")
        val ex = DataCorruptionException("identity corruption!", cause)
        assertEquals("identity corruption!", ex.message)
        assertEquals(cause, ex.cause)
    }
}
```

- [ ] **Step 3: Create `ExpressionParseExceptionTest.kt`**

```kotlin
package dev.skynomads.beerouter.router.exceptions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ExpressionParseExceptionTest {
    @Test
    fun `ExpressionParseException is a RuntimeException`() {
        val ex = ExpressionParseException("parse error")
        assertIs<RuntimeException>(ex)
    }

    @Test
    fun `ExpressionParseException preserves message and cause`() {
        val cause = IllegalArgumentException("bad token")
        val ex = ExpressionParseException("ParseException at line 5: bad token", cause)
        assertEquals("ParseException at line 5: bad token", ex.message)
        assertEquals(cause, ex.cause)
    }
}
```

- [ ] **Step 4: Create `CacheStateExceptionTest.kt`**

```kotlin
package dev.skynomads.beerouter.router.exceptions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CacheStateExceptionTest {
    @Test
    fun `CacheStateException is a RuntimeException`() {
        val ex = CacheStateException("bad state")
        assertIs<RuntimeException>(ex)
    }

    @Test
    fun `CacheStateException preserves message and cause`() {
        val cause = IllegalStateException("root")
        val ex = CacheStateException("mismatch in variable-count: 3<->5", cause)
        assertEquals("mismatch in variable-count: 3<->5", ex.message)
        assertEquals(cause, ex.cause)
    }
}
```

- [ ] **Step 5: Create `DataFileNotFoundExceptionTest.kt`**

```kotlin
package dev.skynomads.beerouter.router.exceptions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DataFileNotFoundExceptionTest {
    @Test
    fun `DataFileNotFoundException is a RuntimeException`() {
        val ex = DataFileNotFoundException("not found")
        assertIs<RuntimeException>(ex)
    }

    @Test
    fun `DataFileNotFoundException preserves message and cause`() {
        val cause = IllegalStateException("root")
        val ex = DataFileNotFoundException("error reading datafile /path/to/file", cause)
        assertEquals("error reading datafile /path/to/file", ex.message)
        assertEquals(cause, ex.cause)
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :core:jvmTest --tests "dev.skynomads.beerouter.router.exceptions.*"`
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/exceptions/
git commit -m "test(core): add tests for new exception types

Co-Authored-By: opencode <opencode@opencode.ai>"
```

---

### Task 3: Replace `RuntimeException` throws with typed exceptions in `BExpressionContext.kt`

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContext.kt`

- [ ] **Step 1: Add imports for new exception types**

Add these imports at the top of `BExpressionContext.kt` (after existing imports):

```kotlin
import dev.skynomads.beerouter.router.exceptions.CacheStateException
import dev.skynomads.beerouter.router.exceptions.ExpressionParseException
```

- [ ] **Step 2: Replace assertion-failed `RuntimeException` at line ~127**

Change:
```kotlin
if (ld2[inum] != ld[inum]) throw RuntimeException(
    "assertion failed encoding inum=" + inum + " val=" + ld[inum] + " " + getKeyValueDescription(
        false,
        ab
    )
)
```
To:
```kotlin
if (ld2[inum] != ld[inum]) throw CacheStateException(
    "assertion failed encoding inum=" + inum + " val=" + ld[inum] + " " + getKeyValueDescription(
        false,
        ab
    )
)
```

- [ ] **Step 3: Replace variable-count mismatch at line ~452**

Change:
```kotlin
if (nv != nv2) throw RuntimeException("mismatch in variable-count: $nv<->$nv2")
```
To:
```kotlin
if (nv != nv2) throw CacheStateException("mismatch in variable-count: $nv<->$nv2")
```

- [ ] **Step 4: Replace variable mismatch at line ~455**

Change:
```kotlin
throw RuntimeException(
    "mismatch in variable " + variableName(i) + " " + variableData[i] + "<->" + otherVariableData[i]
            + "\ntags = " + getKeyValueDescription(false, requireNotNull(encode()))
)
```
To:
```kotlin
throw CacheStateException(
    "mismatch in variable " + variableName(i) + " " + variableData[i] + "<->" + otherVariableData[i]
            + "\ntags = " + getKeyValueDescription(false, requireNotNull(encode()))
)
```

- [ ] **Step 5: Replace "no variable for index" at line ~469**

Change:
```kotlin
throw RuntimeException("no variable for index$idx")
```
To:
```kotlin
throw CacheStateException("no variable for index$idx")
```

- [ ] **Step 6: Replace broad `catch (e: Exception)` at line ~733-734**

Change:
```kotlin
} catch (e: IllegalArgumentException) {
    throw IllegalArgumentException("ParseException at line $linenr: ${e.message}")
} catch (e: Exception) {
    throw RuntimeException(e)
}
```
To:
```kotlin
} catch (e: IllegalArgumentException) {
    throw IllegalArgumentException("ParseException at line $linenr: ${e.message}")
} catch (e: Exception) {
    throw ExpressionParseException("ParseException at line $linenr", e)
}
```

- [ ] **Step 7: Narrow `catch (e: Exception)` in `getLookupKey` at line ~199**

Change:
```kotlin
public fun getLookupKey(name: String?): Int {
    return try {
        lookupNumbers[name]!!
    } catch (e: Exception) {
        -1
    }
}
```
To:
```kotlin
public fun getLookupKey(name: String?): Int {
    return try {
        lookupNumbers[name]!!
    } catch (_: NullPointerException) {
        -1
    }
}
```

- [ ] **Step 8: Narrow `catch (e: Exception)` in `addLookupValue` at line ~604**

Change:
```kotlin
} catch (e: Exception) {
    println("error for $name  $org trans $value ${e.message}")
    lookupData2[num] = 0
}
```
To:
```kotlin
} catch (e: NumberFormatException) {
    println("error for $name  $org trans $value ${e.message}")
    lookupData2[num] = 0
}
```

- [ ] **Step 9: Run tests**

Run: `./gradlew :core:jvmTest --tests "dev.skynomads.beerouter.expressions.*"`
Expected: All tests PASS

- [ ] **Step 10: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContext.kt
git commit -m "refactor(core): replace RuntimeException with typed exceptions in BExpressionContext

Replace RuntimeException with CacheStateException for state violations,
ExpressionParseException for parse errors. Narrow broad catches to
specific exception types.

Co-Authored-By: opencode <opencode@opencode.ai>"
```

---

### Task 4: Replace `RuntimeException` throws with typed exceptions in `BExpression.kt`

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpression.kt`

- [ ] **Step 1: Chain the cause in the NumberFormatException catch at line ~290-291**

Change:
```kotlin
} catch (nfe: NumberFormatException) {
    throw IllegalArgumentException("unknown expression: $operator")
}
```
To:
```kotlin
} catch (nfe: NumberFormatException) {
    throw IllegalArgumentException("unknown expression: $operator", nfe)
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew :core:jvmTest --tests "dev.skynomads.beerouter.expressions.*"`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpression.kt
git commit -m "fix(core): chain NumberFormatException cause in BExpression parse error

Co-Authored-By: opencode <opencode@opencode.ai>"
```

---

### Task 5: Replace `RuntimeException` throws with typed exceptions in codec package

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/TagValueCoder.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/MicroCache2.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/MicroCache.kt`

- [ ] **Step 1: Add import and replace in `TagValueCoder.kt` line ~235**

Add import at top:
```kotlin
import dev.skynomads.beerouter.router.exceptions.DataCorruptionException
```

Change:
```kotlin
throw RuntimeException("identity corruption!")
```
To:
```kotlin
throw DataCorruptionException("identity corruption!")
```

- [ ] **Step 2: Add import and replace in `MicroCache2.kt` line ~456**

Add import at top:
```kotlin
import dev.skynomads.beerouter.router.exceptions.CacheStateException
```

Change:
```kotlin
if (nodeIdx == n) throw RuntimeException("ups: self ref?")
```
To:
```kotlin
if (nodeIdx == n) throw CacheStateException("self reference in node encoding")
```

- [ ] **Step 3: Replace `IllegalArgumentException` stubs in `MicroCache.kt` lines ~173-200**

These four `open` methods on `MicroCache` throw `IllegalArgumentException` as "unimplemented by empty cache" stubs. They should throw `UnsupportedOperationException` instead — that's what it semantically means (the base class doesn't support this operation). The subclass `MicroCache2` overrides these, so callers with a non-empty cache never hit this.

Change all four methods:

```kotlin
public open fun expandId(id32: Int): Long {
    throw UnsupportedOperationException("expandId for empty cache")
}

public open fun shrinkId(id64: Long): Int {
    throw UnsupportedOperationException("shrinkId for empty cache")
}

public open fun isInternal(position: Position): Boolean {
    throw UnsupportedOperationException("isInternal for empty cache")
}

public open fun encodeMicroCache(buffer: ByteArray): Int {
    throw UnsupportedOperationException("encodeMicroCache for empty cache")
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:jvmTest --tests "dev.skynomads.beerouter.codec.*"`
Expected: All tests PASS (the `LinkedListContainerTest` that uses `assertFailsWith<IllegalArgumentException>` should still pass since it tests `require()` not `MicroCache`)

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/TagValueCoder.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/MicroCache2.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/MicroCache.kt
git commit -m "refactor(core): replace RuntimeException with typed exceptions in codec package

Replace DataCorruptionException for TagValueCoder identity check,
CacheStateException for MicroCache2 self-ref, and
UnsupportedOperationException for MicroCache base class stubs.

Co-Authored-By: opencode <opencode@opencode.ai>"
```

---

### Task 6: Replace `RuntimeException` throws with typed exceptions in map package

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/NodesCache.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/PhysicalFile.kt`
- Modify: `core/src/jvmMain/kotlin/dev/skynomads/beerouter/map/Rd5DiffValidator.kt`
- Modify: `core/src/jvmMain/kotlin/dev/skynomads/beerouter/map/Rd5DiffTool.kt`

- [ ] **Step 1: Replace catch-and-rethrow pattern in `NodesCache.kt` lines ~202-206**

Add import at top:
```kotlin
import dev.skynomads.beerouter.router.exceptions.DataFileNotFoundException
```

Change:
```kotlin
} catch (re: RuntimeException) {
    throw re
} catch (e: Exception) {
    throw RuntimeException("error reading datafile $currentFileName: $e", e)
}
```
To:
```kotlin
} catch (re: RuntimeException) {
    throw re
} catch (e: Exception) {
    throw DataFileNotFoundException("error reading datafile $currentFileName", e)
}
```

Note: The message no longer redundantly interpolates `$e` since the cause is now properly chained.

- [ ] **Step 2: Narrow broad catch in `PhysicalFile.kt` line ~101**

Change:
```kotlin
} catch (e: Exception) {
    -1
}
```
To:
```kotlin
} catch (_: IOException) {
    -1
}
```

- [ ] **Step 3: Replace `RuntimeException` in `Rd5DiffValidator.kt` line ~48**

Add import at top:
```kotlin
import dev.skynomads.beerouter.router.exceptions.DataCorruptionException
```

Change:
```kotlin
throw RuntimeException("**************** md5 mismatch!! *****************")
```
To:
```kotlin
throw DataCorruptionException("md5 mismatch in $name")
```

- [ ] **Step 4: Replace `RuntimeException` throws in `Rd5DiffTool.kt` lines ~315, ~327, ~336**

Add import at top:
```kotlin
import dev.skynomads.beerouter.router.exceptions.DataCorruptionException
```

Change:
```kotlin
throw RuntimeException("size mismatch at $subFileIdx/$tileIdx $targetSize!=$newTargetSize")
```
To:
```kotlin
throw DataCorruptionException("size mismatch at $subFileIdx/$tileIdx $targetSize!=$newTargetSize")
```

Change:
```kotlin
throw RuntimeException("size mismatch at $subFileIdx/$tileIdx $targetSize>0")
```
To:
```kotlin
throw DataCorruptionException("size mismatch at $subFileIdx/$tileIdx $targetSize>0")
```

Change:
```kotlin
throw RuntimeException("size mismatch at $subFileIdx/$tileIdx $targetSize<>${len + 4}")
```
To:
```kotlin
throw DataCorruptionException("size mismatch at $subFileIdx/$tileIdx $targetSize<>${len + 4}")
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :core:jvmTest`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/map/NodesCache.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/map/PhysicalFile.kt core/src/jvmMain/kotlin/dev/skynomads/beerouter/map/Rd5DiffValidator.kt core/src/jvmMain/kotlin/dev/skynomads/beerouter/map/Rd5DiffTool.kt
git commit -m "refactor(core): replace RuntimeException with typed exceptions in map package

Replace DataFileNotFoundException in NodesCache, DataCorruptionException
in Rd5DiffValidator/Rd5DiffTool, and narrow PhysicalFile catch to IOException.

Co-Authored-By: opencode <opencode@opencode.ai>"
```

---

### Task 7: Replace misused `IllegalArgumentException` with `RoutingException` in `RoutingEngine.kt`

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt`

- [ ] **Step 1: Add import for RoutingException**

Add at top:
```kotlin
import dev.skynomads.beerouter.router.exceptions.RoutingException
```

- [ ] **Step 2: Replace `IllegalArgumentException("no track found at pass=$cfi")` at line ~1494**

Change:
```kotlin
throw IllegalArgumentException("no track found at pass=$cfi")
```
To:
```kotlin
throw RoutingException("no track found at pass=$cfi")
```

- [ ] **Step 3: Replace `IllegalArgumentException("memory limit reached")` at line ~1818**

Change:
```kotlin
throw IllegalArgumentException("memory limit reached")
```
To:
```kotlin
throw RoutingException("memory limit reached")
```

- [ ] **Step 4: Fix the `dirtyMessage` stored-exception pattern at lines ~1455-1510**

Change the type of `dirtyMessage` from `IllegalArgumentException?` to `RoutingException?`:

Change:
```kotlin
var dirtyMessage: IllegalArgumentException? = null
```
To:
```kotlin
var dirtyMessage: RoutingException? = null
```

Change the catch block:
```kotlin
} catch (iae: IllegalArgumentException) {
```
To:
```kotlin
} catch (re: RoutingException) {
```

Change the body of that catch block:
```kotlin
            if (matchPath != null) {
                track = mergeTrack(matchPath!!, nearbyTrack)
                isDirty = true
                dirtyMessage = iae
```
To:
```kotlin
            if (matchPath != null) {
                track = mergeTrack(matchPath!!, nearbyTrack)
                isDirty = true
                dirtyMessage = re
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :core:jvmTest --tests "dev.skynomads.beerouter.router.*"`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt
git commit -m "refactor(core): replace misused IllegalArgumentException with RoutingException

Routing failures (no track found, memory limit) are not invalid arguments —
use RoutingException. Update dirtyMessage pattern accordingly.

Co-Authored-By: opencode <opencode@opencode.ai>"
```

---

### Task 8: Fix `RoutingContext.kt` — RuntimeException in setModel and broad catch in nogo parsing

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt`

- [ ] **Step 1: Replace `RuntimeException` in `setModel` at line ~400**

The `setModel` method uses `RuntimeException` for an unsupported model name. This is semantically an illegal argument (bad string input), so `IllegalArgumentException` is correct. Just change from `RuntimeException` to `IllegalArgumentException`:

Change:
```kotlin
else -> throw RuntimeException("Cannot create path-model: unsupported model '$className'")
```
To:
```kotlin
else -> throw IllegalArgumentException("Cannot create path-model: unsupported model '$className'")
```

- [ ] **Step 2: Narrow the `catch (e: Exception)` in `prepareNogoPoints` at line ~693**

Change:
```kotlin
try {
    ir = s.substring(4).toInt()
} catch (e: Exception) { /* ignore */
}
```
To:
```kotlin
try {
    ir = s.substring(4).toInt()
} catch (_: NumberFormatException) { /* ignore */
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :core:jvmTest --tests "dev.skynomads.beerouter.router.RoutingContext*"`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt
git commit -m "refactor(core): fix RoutingContext exceptions

Use IllegalArgumentException for unsupported model (not RuntimeException),
narrow nogo parsing catch to NumberFormatException.

Co-Authored-By: opencode <opencode@opencode.ai>"
```

---

### Task 9: Narrow broad catch blocks in `AreaReader.kt` and `RoutingEngine.kt`

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AreaReader.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt`

- [ ] **Step 1: Narrow first broad catch in `AreaReader.kt` line ~155**

This catch wraps the entire `getDirectData` call chain which can throw `IOException` from file I/O. Runtime errors should propagate.

Change:
```kotlin
} catch (e: Exception) {
    println("AreaReader error after used=$used / count=$count: ${e.message}")
    ais.clear()
}
```
To:
```kotlin
} catch (e: IOException) {
    println("AreaReader error after used=$used / count=$count: ${e.message}")
    ais.clear()
}
```

Add `import okio.IOException` if not already imported (check existing imports — this file reads from files using okio's `Source`/`Sink`). Check what IOException is used in this file.

Note: Look at the imports in AreaReader.kt — it uses okio Source/Sink, so it may use `okio.IOException` or `java.io.IOException`. The jvmMain platform uses `java.io.IOException`, commonMain uses `okio.IOException`. Since `AreaReader.kt` is in `commonMain`, use `okio.IOException`.

Actually wait — let me check where AreaReader.kt lives.

- [ ] **Step 1 (revised): Check which IOException is used and narrow the first catch in `AreaReader.kt`**

The file is in `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AreaReader.kt`. Check existing imports for the IOException class used. It likely uses `okio.IOException` since it's commonMain code. Add that import if missing and change:

```kotlin
} catch (e: Exception) {
    println("AreaReader error after used=$used / count=$count: ${e.message}")
    ais.clear()
}
```
To:
```kotlin
} catch (e: IOException) {
    println("AreaReader error after used=$used / count=$count: ${e.message}")
    ais.clear()
}
```

- [ ] **Step 2: Narrow second broad catch in `AreaReader.kt` line ~245**

Change:
```kotlin
} catch (e: Exception) {
    println("AreaReader: ${e.message}")
}
```
To:
```kotlin
} catch (e: IOException) {
    println("AreaReader: ${e.message}")
}
```

- [ ] **Step 3: Narrow third broad catch in `AreaReader.kt` line ~303**

Change:
```kotlin
} catch (_: Exception) {
    ais.clear()
}
```
To:
```kotlin
} catch (_: IOException) {
    ais.clear()
}
```

- [ ] **Step 4: Remove or narrow silent catch in `RoutingEngine.kt` line ~414**

The area sink write is best-effort, but we should at least narrow the catch to `IOException`:

Change:
```kotlin
} catch (e: Exception) {
    // Silently ignore exceptions
}
```
To:
```kotlin
} catch (_: IOException) {
    // Silently ignore I/O errors writing area info
}
```

Add `import okio.IOException` or the appropriate import for the IOException used in this file.

Actually — `RoutingEngine.kt` is in commonMain and reads from PhysicalFile which uses `okio.IOException`. Check existing imports. Since this is a multiplatform module, we need to use the `okio.IOException` that's available in commonMain.

Wait — actually, `java.io.IOException` is used in the existing code like `OsmFile.kt` and `PhysicalFile.kt`. Let me check. Those are in commonMain... Let me check the imports.

Since the project uses okio, and the commonMain code should use `okio.IOException`, but the existing code at `OsmFile.kt` line 68 uses `IOException` — I need to check which import that resolves to.

The safest approach: add the appropriate `IOException` import based on what the existing code uses. If unsure, use `okio.IOException` since this is commonMain code that also targets native.

- [ ] **Step 5: Run tests**

Run: `./gradlew :core:jvmTest`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AreaReader.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt
git commit -m "refactor(core): narrow broad Exception catches to IOException

AreaReader and RoutingEngine silently caught all exceptions, masking
programming errors. Narrow to IOException so runtime errors propagate.

Co-Authored-By: opencode <opencode@opencode.ai>"
```

---

### Task 10: Add `@throws` KDoc to all public API methods

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/SearchBoundary.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/MicroCache.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/LinkedListContainer.kt`

- [ ] **Step 1: Add `@throws` to `RoutingEngine.doRouting()` at line ~109**

```kotlin
/**
 * Route between the given waypoints.
 *
 * @param waypoints the list of waypoints to route between
 * @return the calculated track, or null if routing fails
 * @throws RoutingException if no route can be found or memory limit is reached
 * @throws RoutingIslandException if the start/end points are on a routing island (isolated network)
 */
public suspend fun doRouting(waypoints: List<OsmNodeNamed>): OsmTrack?
```

- [ ] **Step 2: Add `@throws` to `RoutingEngine.doGetInfo()` at line ~166**

```kotlin
/**
 * Get routing info for a waypoint.
 *
 * @param waypoints the list of waypoints
 * @return the calculated track with info, or null
 * @throws RoutingException if no route can be found or memory limit is reached
 * @throws RoutingIslandException if the waypoint is on a routing island
 */
public suspend fun doGetInfo(waypoints: List<OsmNodeNamed>): OsmTrack?
```

- [ ] **Step 3: Add `@throws` to `RoutingEngine.doRoundTrip()` at line ~252**

```kotlin
/**
 * Calculate a round-trip route from the given waypoint.
 *
 * @param waypoints the list of waypoints (typically a single starting point)
 * @return the calculated round-trip track, or null if routing fails
 * @throws RoutingException if no route can be found or memory limit is reached
 * @throws RoutingIslandException if the waypoint is on a routing island
 */
public suspend fun doRoundTrip(waypoints: List<OsmNodeNamed>): OsmTrack?
```

- [ ] **Step 4: Add `@throws` to `RoutingContext.setModel()` (private but important)**

This is a private method, so `@throws` is still useful for maintainers:

```kotlin
/**
 * Set the path model by class name.
 *
 * @param className the model class name, or null for StdModel
 * @throws IllegalArgumentException if the model class name is not supported
 */
private fun setModel(className: String?) {
```

- [ ] **Step 5: Add `@throws` to `RoutingContext.prepareNogoPoints()`**

```kotlin
/**
 * Prepare nogo points by parsing radius values from their names.
 * Radius values after the 4th character of the name are parsed as integers.
 * Parse errors are silently ignored and the default radius of 20m is used.
 *
 * @param nogos the list of nogo points to prepare
 */
public fun prepareNogoPoints(nogos: MutableList<OsmNodeNamed>) {
```

- [ ] **Step 6: Add `@throws` to `SearchBoundary.Direction.fromId()`**

```kotlin
/**
 * Get a Direction by its integer ID.
 *
 * @param id the direction ID (0-3)
 * @throws IllegalArgumentException if the ID does not correspond to a valid Direction
 */
fun fromId(id: Int): Direction =
```

- [ ] **Step 7: Add `@throws` to `MicroCache` open methods**

```kotlin
/**
 * Expand a 32-bit micro-cache-internal id into a 64-bit (lon|lat) global-id.
 *
 * @param id32 the internal id to expand
 * @return the global id
 * @throws UnsupportedOperationException if called on an empty cache
 * @see .shrinkId
 */
public open fun expandId(id32: Int): Long {

/**
 * Shrink a 64-bit (lon|lat) global-id into a 32-bit micro-cache-internal id.
 *
 * @param id64 the global id to shrink
 * @return the internal id
 * @throws UnsupportedOperationException if called on an empty cache
 * @see .expandId
 */
public open fun shrinkId(id64: Long): Int {

/**
 * Check if the given lon/lat position is internal for that micro-cache.
 *
 * @param position the position to check
 * @return true if the position is internal
 * @throws UnsupportedOperationException if called on an empty cache
 */
public open fun isInternal(position: Position): Boolean {

/**
 * Encode the micro-cache into the format used in the datafiles.
 *
 * @param buffer byte array to encode into (considered big enough)
 * @return the size of the encoded data
 * @throws UnsupportedOperationException if called on an empty cache
 */
public open fun encodeMicroCache(buffer: ByteArray): Int {
```

- [ ] **Step 8: Fix existing `@throws` on `LinkedListContainer.dataElement` at line ~65**

The existing KDoc says `@throws IllegalArgumentException if no more element` but the code uses `require()` which throws `IllegalArgumentException`. This is correct, but the grammar should be improved:

Change:
```kotlin
 * @throws IllegalArgumentException if no more element
```
To:
```kotlin
 * @throws IllegalArgumentException if no more elements are available
```

- [ ] **Step 9: Run tests**

Run: `./gradlew :core:jvmTest`
Expected: All tests PASS

- [ ] **Step 10: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/SearchBoundary.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/MicroCache.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/LinkedListContainer.kt
git commit -m "docs(core): add @throws KDoc to public API methods

Document thrown exceptions on RoutingEngine public methods,
RoutingContext.setModel, SearchBoundary.Direction.fromId,
MicroCache open methods, and LinkedListContainer.dataElement.

Co-Authored-By: opencode <opencode@opencode.ai>"
```

---

### Task 11: Full verification and final commit

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew :core:jvmTest`
Expected: All tests PASS

- [ ] **Step 2: Run lint/typecheck if available**

Run: `./gradlew :core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Verify no remaining `RuntimeException` throws in production code (excluding the `RoutingIslandException` superclass reference)**

Run: `rg "throw RuntimeException" core/src/commonMain/ core/src/jvmMain/ core/src/nativeMain/`
Expected: No matches (or only in platform-specific code that appropriately uses RuntimeException)

- [ ] **Step 4: Verify no remaining `catch (e: Exception)` in production commonMain code**

Run: `rg "catch \(e: Exception\)" core/src/commonMain/`
Expected: No matches (all narrowed to specific types)

---

## Self-Review Checklist

- [x] **Spec coverage:** Every exception site identified in the audit has a corresponding task step
- [x] **Placeholder scan:** No TBDs, no "add appropriate error handling", no "similar to Task N"
- [x] **Type consistency:** `RoutingException`, `DataCorruptionException`, `ExpressionParseException`, `CacheStateException`, `DataFileNotFoundException` all defined in Task 1 and used consistently in Tasks 3-6; `RoutingIslandException` updated to extend `RoutingException` in Task 1 and referenced correctly in Task 10
- [ ] **Import resolution:** Need to verify `IOException` import in `AreaReader.kt` and `RoutingEngine.kt` during execution — both are in `commonMain` and should use `okio.IOException`