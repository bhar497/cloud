export function addElements() {
	$('<link rel="apple-touch-icon" sizes="180x180" href="hosts/adaptivecloud/apple-touch-icon.png">').appendTo('head')
	$('<link rel="icon" type="image/png" sizes="32x32" href="hosts/adaptivecloud/favicon-32x32.png">').appendTo('head')
	$('<link rel="icon" type="image/png" sizes="16x16" href="hosts/adaptivecloud/favicon-16x16.png">').appendTo('head')
	$('<link rel="manifest" href="hosts/adaptivecloud/site.webmanifest">').appendTo('head')
	$('<link rel="mask-icon" href="hosts/adaptivecloud/safari-pinned-tab.svg" color="#5bbad5">').appendTo('head')
	$('<link rel="shortcut icon" href="hosts/adaptivecloud/favicon.ico">').appendTo('head')
	$('<meta name="msapplication-TileColor" content="#da532c">').appendTo('head')
	$('<meta name="msapplication-config" content="hosts/adaptivecloud/browserconfig.xml">').appendTo('head')
	$('<meta name="theme-color" content="#ffffff">').appendTo('head')
}
export function removeOverlapping() {
	$('link[rel="apple-touch-icon"]').remove()
	$('link[rel="shortcut"]').remove()
	$('link[rel="shortcut icon"]').remove()
	$('link[rel="icon"][sizes="16x16"]').remove()
	$('link[rel="icon"][sizes="32x32"]').remove()
	$('meta[name="msapplication-TileColor"]').remove()
	$('meta[name="msapplication-config"]').remove()
	$('link[rel="manifest"]').remove()
	$('meta[name="theme-color"]').remove()
	$('link[rel="mask-icon"]').remove()
}