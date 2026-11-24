/* App bootstrap: keep startup after the screen modules are registered. */
loadPrefs();
if (!location.hash) location.hash = '#explore';
go();
