# Modules

| Feature | Android | Server and dependencies |
|---|---|---|
| Accounts and session | `features/auth` contains login/signup, auth requests and session restoration. | `features/accounts` handles registration, credentials, BCrypt, JWT, public profile responses and optional welcome email. Uses users in MongoDB. |
| Groups | `features/groups` contains dashboard, creation, group repository and group model. The legacy Add Hisab composable is retained here. | `features/groups` handles group lifecycle and membership. Reads or modifies group and entry documents. |
| Ledger | `features/ledger` contains group-detail UI, dialogs, balance display, repository contract and remote/cache reconciliation. | `features/ledger` validates and persists entries. Uses group membership and MongoDB entries. |
| Invitations | `features/invitations` contains notification UI, request state and repository. Its domain record is shared by the data and presentation packages. | `features/invitations` uses account lookup, group membership and request documents. It emits socket events to recipients. |
| Profile and media | `features/profile` reads or updates the profile, theme and selected picture. Its repository owns URI reading and multipart uploads. | Account routes expose profile data. `features/media` receives images and serves portable paths. |
| Admin | `features/admin` displays Room ledger records for an authorized admin session. | The role comes from the persisted user document. The panel's data source is the device cache. |
| Realtime and polling | Dashboard and notification ViewModels poll while active. The socket transport is available in the app module. | `features/realtime` authenticates sockets and delivers in-process events. |

The feature packages depend on the shared transport, domain records and persistence contracts. A repository maps API DTOs before returning feature records. Server plugins assemble the routes and apply shared HTTP policies. Mongo document types stay in one package to keep persistence mapping stable.
