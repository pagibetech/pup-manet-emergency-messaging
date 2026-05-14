# STEP 034 Prep - Baseline Test GitHub Evidence Note

Date: 2026-05-14

Workbook step: 8.1 / Test Campaign / Baseline Test

## Scope

Updated the workbook instructions for the upcoming baseline physical test so the test evidence must include the GitHub repository state used during the test.

## Workbook Updates

- `Detailed Steps` row `8.1`
  - Procedure now requires recording the GitHub repo, branch, and commit SHA used for the baseline test build.
  - Evidence now includes the test report, phone screenshots, ESP32/RPi logs, GitHub repo URL, branch, and commit SHA.
  - Notes clarify that the baseline remains `Not Started` until user physical test evidence is provided.
- `Test Campaign` row `Baseline Operation`
  - Evidence to capture now includes GitHub repo URL, branch, and commit SHA.
  - Status remains pending because the physical baseline test has not been executed yet.
- `Progress Tracker`
  - Added a preparation entry for Step 8.1 evidence requirements.

## GitHub Evidence Requirement

For Step 8.1, record:

- Repository: `https://github.com/pagibetech/pup-manet-emergency-messaging`
- Branch used for the test
- Commit SHA used for the test
- Screenshots and terminal logs from the physical baseline run

## Validation

Workbook was edited in place:

- `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`

No Android, ESP32, or Raspberry Pi code was changed.
