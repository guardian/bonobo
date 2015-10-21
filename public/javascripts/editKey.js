var rateLimitsRow = document.getElementById("defaultRequests_field");
var rateLimitsCheckbox = document.getElementById("defaultRequests");

document.addEventListener("DOMContentLoaded", function() {
    rateLimitsRow.style.display = "none";
});

document.getElementById("tier").addEventListener("change", function() {
    rateLimitsRow.style.display = "block";
});
function changeReadOnlyTo(bool) {
    document.getElementById("requestsPerDay").readOnly = bool;
    document.getElementById("requestsPerMinute").readOnly = bool;
}
rateLimitsCheckbox.addEventListener("change", function() {
    rateLimitsCheckbox.checked ? changeReadOnlyTo(true) : changeReadOnlyTo(false)
});