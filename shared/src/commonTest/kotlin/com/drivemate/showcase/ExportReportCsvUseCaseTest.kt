package com.drivemate.showcase

import com.drivemate.showcase.domain.usecase.report.ExportReportCsvRequest
import com.drivemate.showcase.domain.usecase.report.ExportReportCsvUseCase
import com.drivemate.showcase.domain.usecase.report.ReportCsvSection
import kotlin.test.Test
import kotlin.test.assertContains

class ExportReportCsvUseCaseTest {

    private val useCase = ExportReportCsvUseCase()

    @Test
    fun exportReportCsv_formatsSectionsInOrder_andPreservesCsvEscaping() {
        val csv = useCase(
            ExportReportCsvRequest(
                sections = listOf(
                    ReportCsvSection(
                        title = "Summary",
                        headers = listOf("Metric", "Value"),
                        rows = listOf(
                            listOf("Month", "April 2026"),
                            listOf("Notes", "Needs \"review\", line 1\nline 2")
                        )
                    ),
                    ReportCsvSection(
                        title = "Drivers",
                        headers = listOf("Driver", "Pending"),
                        rows = listOf(listOf("Driver Alpha", "3"))
                    )
                )
            )
        )

        assertContains(csv, "Summary\nMetric,Value\nMonth,April 2026")
        assertContains(csv, "Notes,\"Needs \"\"review\"\", line 1\nline 2\"")
        assertContains(csv, "\n\nDrivers\nDriver,Pending\nDriver Alpha,3")
    }
}
