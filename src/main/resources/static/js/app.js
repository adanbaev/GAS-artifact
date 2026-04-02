$(document).on({
    ajaxStart: function() {
        indicator.showPleaseWait();
        $('#errorNotification').hide();
    },
    ajaxStop: function() {
        indicator.hidePleaseWait();
    }
});

var indicator;
indicator = indicator || (function () {
    return {
        showPleaseWait: function() {
            $("#indicatorModal").show()
        },
        hidePleaseWait: function () {
            $("#indicatorModal").hide();
        }
    };
})();

$(document).on("change", "#pageSizeSelect", function (event) {
    $("#pageSize").val(this.value);
    $("#page").val(1);
    search();
});

$(document).on("click", ".page-link", function (event) {
    $("#pageSize").val($(this).attr("pageSize"));
    $("#page").val($(this).attr("page"));
    search();
});

$(document).keypress(function(event) {
    if(event.which == 13 && $(event.target).hasClass("form-control") && $(event.target).is("input")) {
        event.preventDefault();
        $("#page").val(1);
        search();
    }
});

$(document).on("change", "#eventSelect", function (event) {
    event.preventDefault();
    $("#page").val(1);
    search();
});

$(document).on("change", "#eventStatusSelect", function (event) {
    event.preventDefault();
    $("#page").val(1);
    search();
});

function showDialog(url) {
    $("#myModal .modal-dialog").html("");
    $("#myModal .modal-dialog").load(url, function () {
        $("#myModal").modal("show");
    });
}

function create(url){
    $("#myModal .modal-dialog").html("");
    $("#myModal .modal-dialog").load(url, function () {
        $("#myModal").modal("show");
    });
}

function restart(url){
    $("#myModal .modal-dialog").html("");
    $("#myModal .modal-dialog").load(url, function () {
        $("#myModal").modal("show");
    });
}

function search(){
    $.ajax({
        url: $('#searchForm').attr('action'),
        data: $('#searchForm').serialize(),
        type: "POST",
        success: function (returnedData) {
            $('#searchResults').html(returnedData);
        }
    });
}

function resetAllValues() {
    $('#filter-fields').find('input, select').val('');
    $('input[type="checkbox"]').prop('checked', false);

    search();
}

/**
 * Dropdown-меню в таблицах режется контейнерами со скроллом (особенно с perfect-scrollbar),
 * т.к. у обёрток часто стоит overflow: hidden/auto.
 *
 * Решение: при открытии dropdown переносим .dropdown-menu в <body> и позиционируем его поверх страницы.
 * Применяем только для меню внутри #searchResults (таблицы результатов поиска, логи, инциденты и т.п.).
 */
(function () {
    if (typeof document === "undefined") return;

    // WeakMap: menu -> state
    var menuState = new WeakMap();
    // WeakMap: toggle -> menu
    var toggleMenu = new WeakMap();

    function isInSearchResults(toggleEl) {
        try {
            return !!(toggleEl && toggleEl.closest && toggleEl.closest("#searchResults"));
        } catch (e) {
            return false;
        }
    }

    function getMenuByToggle(toggleEl) {
        return toggleMenu.get(toggleEl);
    }

    function safeHideDropdown(toggleEl) {
        try {
            if (typeof bootstrap !== "undefined" && bootstrap.Dropdown) {
                var inst = bootstrap.Dropdown.getInstance(toggleEl);
                if (inst) inst.hide();
            }
        } catch (e) {
            // ничего
        }
    }

    function portalize(toggleEl) {
        if (!toggleEl || !toggleEl.closest) return;

        var dropdown = toggleEl.closest(".dropdown");
        if (!dropdown) return;

        var menu = dropdown.querySelector(".dropdown-menu");
        if (!menu) return;

        // уже портализовано
        if (menuState.has(menu)) return;

        var st = {
            parent: menu.parentNode,
            nextSibling: menu.nextSibling,
            style: menu.getAttribute("style") || "",
            onScroll: null,
            onResize: null
        };

        // переносим в body (чтобы не резалось overflow'ом)
        document.body.appendChild(menu);

        // фиксируем поверх страницы
        menu.classList.add("dropdown-menu-portal");
        menu.style.position = "fixed";
        menu.style.margin = "0";
        menu.style.transform = "none";
        menu.style.zIndex = "1060"; // выше модалки (1055), чтобы не пропадало внутри модалок
        menu.style.maxHeight = "calc(100vh - 160px)";
        menu.style.overflowY = "auto";

        // пока не спозиционируем — прячем (чтобы не было “мигания”)
        menu.style.visibility = "hidden";
        menu.style.left = "0px";
        menu.style.top = "0px";

        // закрывать на любом скролле/ресайзе, чтобы меню не “уехало”
        st.onScroll = function () { safeHideDropdown(toggleEl); };
        st.onResize = function () { safeHideDropdown(toggleEl); };

        window.addEventListener("scroll", st.onScroll, true); // capture: ловим прокрутку любых контейнеров
        window.addEventListener("resize", st.onResize);

        menuState.set(menu, st);
        toggleMenu.set(toggleEl, menu);
    }

    function positionMenu(toggleEl, menu) {
        if (!toggleEl || !menu) return;

        var rect = toggleEl.getBoundingClientRect();

        // размеры меню (уже должны быть, т.к. событие shown)
        var menuRect = menu.getBoundingClientRect();
        var mw = menuRect.width || menu.offsetWidth || 220;
        var mh = menuRect.height || menu.offsetHeight || 120;

        var padding = 8;
        var vw = document.documentElement.clientWidth;
        var vh = document.documentElement.clientHeight;

        // вправо/влево: стараемся прижать к правому краю кнопки (как dropdown-menu-end)
        var left = rect.right - mw;
        if (left < padding) left = padding;
        if (left + mw > vw - padding) left = Math.max(padding, vw - padding - mw);

        // вверх/вниз: если снизу места мало — открываем вверх
        var spaceBelow = vh - rect.bottom;
        var spaceAbove = rect.top;

        var top;
        var openUp = (spaceBelow < mh) && (spaceAbove >= mh);
        if (openUp) top = rect.top - mh - 6;
        else top = rect.bottom + 6;

        // поджимаем в границы экрана
        if (top < padding) top = padding;
        if (top + mh > vh - padding) top = Math.max(padding, vh - padding - mh);

        menu.style.left = Math.round(left) + "px";
        menu.style.top = Math.round(top) + "px";
        menu.style.visibility = "visible";
    }

    function restore(toggleEl) {
        var menu = getMenuByToggle(toggleEl);
        if (!menu) return;

        var st = menuState.get(menu);
        if (!st) return;

        window.removeEventListener("scroll", st.onScroll, true);
        window.removeEventListener("resize", st.onResize);

        // вернуть в исходное место в DOM
        try {
            if (st.nextSibling) st.parent.insertBefore(menu, st.nextSibling);
            else st.parent.appendChild(menu);
        } catch (e) {
            // если родителя уже нет (контент перерендерили) — просто оставим в body
        }

        // восстановить стиль
        menu.classList.remove("dropdown-menu-portal");
        if (st.style && st.style.length > 0) menu.setAttribute("style", st.style);
        else menu.removeAttribute("style");

        menuState.delete(menu);
        toggleMenu.delete(toggleEl);
    }

    // До показа: переносим меню в body и прячем
    document.addEventListener("show.bs.dropdown", function (event) {
        var toggleEl = event.target;
        if (!isInSearchResults(toggleEl)) return;
        portalize(toggleEl);
    }, true);

    // После показа: позиционируем и показываем
    document.addEventListener("shown.bs.dropdown", function (event) {
        var toggleEl = event.target;
        if (!isInSearchResults(toggleEl)) return;

        var menu = getMenuByToggle(toggleEl);
        if (!menu) return;

        positionMenu(toggleEl, menu);
    }, true);

    // После скрытия: возвращаем меню назад
    document.addEventListener("hidden.bs.dropdown", function (event) {
        var toggleEl = event.target;
        if (!isInSearchResults(toggleEl)) return;
        restore(toggleEl);
    }, true);

})();
