//
// Use https://realfavicongenerator.net to generate custom host based resources
//
const defaulthost = 'ippathways';
const defaultbrand = 'adaptivecloud';
var hostparts = window.location.hostname.split('.');
var custombrand = hostparts.length >= 3 ? hostparts[1] : hostparts[0];
if (parseInt(custombrand) || custombrand == defaulthost) {
    // custombrand is an IP address or our own name (not a white-label), so use adaptive cloud base level rebrand
    custombrand = defaultbrand;
}

function getHostPath(host) {
    return `hosts/${host}`;
}

function customizeBrand(brandpath) {
    // Pull in the custom css
    $('<link rel="stylesheet" type="text/css" rel="stylesheet" href="'+brandpath+'/custom.css" >').appendTo('head');

    import(`./${brandpath}/updates.js`)
        .then(m => {
            // Remove all elements matching ones in 'replaces'
            m.removeOverlapping();

            // Add all elements in 'elements'
            m.addElements();
        })
}

function finishSetup() {
    // Set the domain if we're on the login form (element exists)
    var domainInput = $('div.login.nologo input[name="domain"]')
    if (domainInput.length > 0 && custombrand) {
        domainInput.val(custombrand)
    }

    // Set the title
    // This doesn't work, as cloud stack resets the title after this event fires!!! ugh
    $('title').text('AdaptiveCloud')
    //$('title').text(custombrand)
}

$(window).bind('cloudStack.cloudStack.init', function() {
    finishSetup()
});

// Verify that there is a custom css file, if so, then try to inject the resources for it
var path = getHostPath(custombrand);
var url = `${path}/custom.css`
$.get(url)
    .done(function() {
        customizeBrand(path);
    })
    .fail(function() {
        if (custombrand != defaultbrand) {
            // Fall back to the default brand
            custombrand = defaultbrand
            path = getHostPath(custombrand);
            url = `${path}/custom.css`
            $.get(url)
            .done(function() {
                customizeBrand(path);
            });
        }
    });
