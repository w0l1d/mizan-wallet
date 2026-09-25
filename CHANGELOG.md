# Changelog

All notable changes to this project will be documented in this file.

---

## [Unreleased]

### Fixed (feat/balance-excluded-accounts)
- **Balance after planned payments no longer counts excluded accounts**: planned payments due on accounts excluded from the balance were added to the projection even though those accounts' money is left out of the current balance

### Added (feat/balance-excluded-accounts)
- **Excluded accounts line on the Balance screen**: shows what the excluded accounts hold, planned payments included, without folding it into the headline figure
- **Balance including excluded accounts**: opt-in second balance on the Balance screen, toggled in Settings → Features (off by default)

### Added (feat/transfer-excluded-calc)
- **Transfers to excluded accounts as expense**: New setting to count money transferred to excluded accounts as expenses in monthly totals
- **Transfers from excluded accounts as income**: New setting to count money received from excluded accounts as income in monthly totals
- Both settings available in Settings → App Settings section with individual toggles

### Added (feat/accounts-date-filter)
- **Period filter on Accounts screen**: date/period selector on the Accounts tab with month navigation, custom range, and a reset button
- Default view shows each account's all-time balance with income/expense for the current month (matching the previous behaviour)
- Selecting a period filters both the balance and the income/expense totals to that range
- Reset returns to the default view

---

*Changelog format based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).*
