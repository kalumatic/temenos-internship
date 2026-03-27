ALTER TABLE timer
ADD COLUMN callback_url TEXT,
ADD COLUMN csrf_token TEXT;
