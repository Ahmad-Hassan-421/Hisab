# Invitation messages

Added an `invite_message_document` MongoDB collection seeded idempotently with exactly 100 unique active English invitation messages. Each new invitation selects one active message at random and stores its text with the request. Android displays the message on invitation cards. Existing invitations without a message continue to work with no message shown.
