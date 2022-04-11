pg_dump -U moqui moqui > /tmp/moqui-dump.sql --exclude-table=public.user_account
pg_dump -U moqui moqui > /tmp/moqui-dump-user.sql --table=public.user_account
