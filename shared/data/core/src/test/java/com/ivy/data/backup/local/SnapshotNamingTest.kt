package com.ivy.data.backup.local

import com.ivy.data.model.backup.SnapshotOrigin
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import java.time.Instant

class SnapshotNamingTest {

    private val device = "a3f2"

    @Test
    fun `builds the documented name`() {
        val name = SnapshotNaming.build(
            capturedAt = Instant.parse("2026-10-03T04:12:00Z"),
            device = device,
            origin = SnapshotOrigin.Scheduled,
        )

        name shouldBe "wallet-20261003T041200Z--a3f2--sched.zip"
    }

    @Test
    fun `names sort chronologically as strings`() {
        val instants = listOf(
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-10-03T04:12:00Z"),
            Instant.parse("2027-02-28T23:59:59Z"),
        )

        val names = instants.map {
            SnapshotNaming.build(it, device, SnapshotOrigin.Manual)
        }

        names.sorted() shouldContainExactly names
    }

    @Test
    fun `names contain no colon and no character outside the portable set`() {
        val portable = Regex("^[A-Za-z0-9.-]+$")

        listOf(SnapshotOrigin.Scheduled, SnapshotOrigin.Manual, SnapshotOrigin.Safety).forEach { origin ->
            val name = SnapshotNaming.build(Instant.parse("2026-10-03T04:12:00Z"), device, origin)

            portable.matches(name) shouldBe true
            name.contains(':') shouldBe false
        }
    }

    @Test
    fun `round-trips to origin and instant`() {
        listOf(
            SnapshotOrigin.Scheduled,
            SnapshotOrigin.Manual,
            SnapshotOrigin.Safety,
        ).forEach { origin ->
            val capturedAt = Instant.parse("2026-10-03T04:12:00Z")
            val name = SnapshotNaming.build(capturedAt, device, origin)

            val parsed = SnapshotNaming.parse(name)

            parsed shouldNotBe null
            parsed!!.capturedAt shouldBe capturedAt
            parsed.origin shouldBe origin
            parsed.device shouldBe device
        }
    }

    @Test
    fun `two captures in the same second produce different names`() {
        val capturedAt = Instant.parse("2026-10-03T04:12:00Z")
        val taken = setOf(SnapshotNaming.build(capturedAt, device, SnapshotOrigin.Scheduled))

        val second = SnapshotNaming.buildUnique(capturedAt, device, SnapshotOrigin.Scheduled, taken)

        second shouldNotBe taken.single()
        taken.contains(second) shouldBe false
    }

    @Test
    fun `a disambiguated name still parses and still sorts after the first`() {
        val capturedAt = Instant.parse("2026-10-03T04:12:00Z")
        val first = SnapshotNaming.build(capturedAt, device, SnapshotOrigin.Scheduled)
        val second = SnapshotNaming.buildUnique(capturedAt, device, SnapshotOrigin.Scheduled, setOf(first))

        SnapshotNaming.parse(second)?.capturedAt shouldBe capturedAt
        listOf(second, first).sorted() shouldContainExactly listOf(first, second)
    }

    @Test
    fun `a foreign file name does not parse`() {
        SnapshotNaming.parse("holiday-photos.zip") shouldBe null
        SnapshotNaming.parse("manifest.json") shouldBe null
        SnapshotNaming.parse("wallet-notadate--a3f2--sched.zip") shouldBe null
    }
}
