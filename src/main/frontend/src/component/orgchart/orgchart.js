import OrgChart from "@balkangraph/orgchart.js";

class Orgchart extends HTMLElement {

    connectedCallback() {
        this.style.display = "block";
        this.style.width = "100%";
        this.style.height = "600px";

        const nodes = [];

        this.chart = new OrgChart(this, {
            template: "clara",
            enableSearch: false,
            tags: {
                group: {
                    subTreeConfig: {
                        layout: OrgChart.layout.treeList,
                        template: 'treeListItem'
                    }
                }
            },
            nodeBinding: {
                field_0: "name",
                field_1: "title"
            },
            nodes: nodes
        });

        // Пример события
        this.chart.on('click', (sender, args) => {
            this.dispatchEvent(new CustomEvent("node-click", {
                detail: args.node,
                bubbles: true
            }));
        });
    }

    setData(json) {
        if (!this.chart) return;
        const nodes = JSON.parse(json);
        this.chart.load(nodes);
    }

    dataJson = [
        { id: 1,  name: "Nicky Phillips", title: "CEO", img: "https://cdn.balkan.app/shared/anim/1.gif" },
        { id: 2, pid: 1, tags: ['group'], name: "John Dow", title: "IT Department manager", img: "https://cdn.balkan.app/shared/a/18.jpg" },
        { id: 3, pid: 1, tags: ['group'], name: "John Smith", title: "Sales department", img: "https://cdn.balkan.app/shared/a/19.jpg" },

        { id: 4, stpid: 2, name: "Cory Robbins", title: "Core Team Lead", img: "https://cdn.balkan.app/shared/a/7.jpg" },
        { id: 9, stpid: 2, name: "Lynn Fleming", title: "UI Team Lead", img: "https://cdn.balkan.app/shared/a/11.jpg" },
        { id: 100, pid: 4, name: "Billie Roach", title: "Backend Senior Developer", img: "https://cdn.balkan.app/shared/a/8.jpg"},
        { id: 101, pid: 4, name: "Maddox Hood", title: "C# Developer", img: "https://cdn.balkan.app/shared/a/9.jpg" },
        { id: 102, pid: 4, name: "Sam Tyson", title: "Backend Junior Developer", img: "https://cdn.balkan.app/shared/a/10.jpg" },
        { id: 103, pid: 9, name: "Jo Baker", title: "JS Developer", img: "https://cdn.balkan.app/shared/a/12.jpg"  },
        { id: 104, pid: 9, name: "Emerson Lewis", title: "Graphic Designer", img: "https://cdn.balkan.app/shared/a/13.jpg" },
        { id: 105, pid: 9, name: "Haiden Atkinson", title: "UX Expert", img: "https://cdn.balkan.app/shared/a/14.jpg" },

        { id: 10, stpid: 3, name: "Tyler Chavez", title: "Sales Manager", img: "https://cdn.balkan.app/shared/a/15.jpg" },
        { id: 11, pid: 10, name: "Raylee Allen", title: "Sales", img: "https://cdn.balkan.app/shared/a/16.jpg" },
        { id: 12, pid: 10, name: "Kris Horne", title: "Sales Guru", img: "https://cdn.balkan.app/shared/a/8.jpg" },
    ];
}

customElements.define("org-chart", Orgchart);