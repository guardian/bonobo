var divAllLabels = document.getElementById("divAllLabels")
var divAllLabelsContainer = document.getElementById("divAllLabelsContainer")
var divChosenLabels = document.getElementById("divChosenLabels")
var btnAddLabels = document.getElementById("btnAddLabels")
var listLabels = document.getElementsByClassName("label")
var hiddenLabelIds = document.getElementById("labelIds")

document.addEventListener("DOMContentLoaded", function() {
    divAllLabels.style.display = 'none'
    document.getElementById("labelIds_field").style.display = 'none'
});

btnAddLabels.addEventListener("click", function(){
    divAllLabels.style.display = 'block'
});

document.getElementById("btnCloseLabels").addEventListener("click", function(){
    divAllLabels.style.display = 'none'
});

for(var i = 0; i < listLabels.length; i++)
{
    listLabels.item(i).addEventListener("click", function(){
        var id = this.dataset.id
        if(this.dataset.used == 'false') {
            divChosenLabels.appendChild(this)
            this.dataset.used = 'true'
            hiddenLabelIds.value = hiddenLabelIds.value + "," + id
        }
        else if(this.dataset.used == 'true') {
            divAllLabelsContainer.appendChild(this)
            this.dataset.used = 'false'
            hiddenLabelIds.value = hiddenLabelIds.value.replace(id, "")
        }
    })
}