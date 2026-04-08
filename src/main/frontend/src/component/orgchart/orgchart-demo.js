OrgChart.templates.umida = Object.assign({}, OrgChart.templates.ula);
OrgChart.templates.umida.size = [300, 140];

OrgChart.templates.umidaTreeListItem = Object.assign({}, OrgChart.templates.treeListItem);
OrgChart.templates.umidaTreeListItem.size = [350, 80];

OrgChart.templates.umidaTreeListItemBig = Object.assign({}, OrgChart.templates.treeListItem);
OrgChart.templates.umidaTreeListItemBig.size = [350, 120];

OrgChart.templates.umida.img_0 =
    `<image preserveAspectRatio="xMidYMid slice" 
        clip-path="url(#_zv25)"
        xlink:href="{val}"
        x="10"
        y="30"
        width="80"
        height="80">
</image>`;
OrgChart.templates.umida.name =
    `<text data-width="180"
      data-text-overflow="multiline"
      style="font-size:16px;font-weight:600;"
      fill="#757575"
      x="90"
      y="60">
      {val}
</text>`;
OrgChart.templates.umida.department =
    `<text data-width="260"
      data-text-overflow="multiline"
      style="font-size:12px;"
      fill="#757575"
      x="20"
      y="20">
      {val}
</text>`;
OrgChart.templates.umida.position =
    `<text data-width="180"
      data-text-overflow="multiline"
      style="font-size:12px;"
      fill="#757575"
      text-align="end"
      x="90"
      y="80">
      {val}
</text>`;


OrgChart.templates.umidaTreeListItem.name =
    `<text data-width="180"
       data-text-overflow="multiline-2"
       style="font-size:14px;font-weight:600;"
       fill="#757575"
       x="40"
       y="30">
       {val}
</text>`;
OrgChart.templates.umidaTreeListItem.position =
    `<text data-width="180"
       data-text-overflow="multiline-2"
       style="font-size:12px;"
       fill="#757575"
       x="40"
       y="50">
       {val}
</text>`;


OrgChart.templates.umidaTreeListItemBig.department =
    `<text data-width="180"
       data-text-overflow="multiline-2"
       style="font-size:12px;"
       fill="#757575"
       x="15"
       y="20">
       {val}
</text>`;
OrgChart.templates.umidaTreeListItemBig.name =
    `<text data-width="180"
       data-text-overflow="multiline-2"
       style="font-size:14px;font-weight:600;"
       fill="#757575"
       x="40"
       y="60">
       {val}
</text>`;
OrgChart.templates.umidaTreeListItemBig.position =
    `<text data-width="180"
       data-text-overflow="multiline-2"
       style="font-size:12px;"
       fill="#757575"
       x="40"
       y="80">
       {val}
</text>`;
OrgChart.templates.umidaTreeListItemBig.img_0 =
    `<image preserveAspectRatio="xMidYMid slice" 
        clip-path="url(#_zv25)"
        xlink:href="{val}"
        x="244"
        y="14"
        width="80"
        height="80">
</image>`;

// -- Control buttons --
let plusIcon = `<i class="material-icons">add</i>`;
let minusIcon = `<i class="material-icons">remove</i>`;

class Orgchart extends HTMLElement {

    connectedCallback() {
        this.style.display = "block";
        this.style.width = "100%";
        this.style.height = "900px";

        this.chart = new OrgChart(this, {
            template: "umida",
            enableSearch: false,
            controls: {
                zoom_in: { title: "Zoom In", icon: plusIcon},
                zoom_out: { title: "Zoom Out", icon: minusIcon},
            },
            tags: {
                group: {
                    subTreeConfig: {
                        layout: OrgChart.layout.treeList,
                        template: 'umidaTreeListItem'
                    }
                },
                innerTree: {
                    template: 'umidaTreeListItemBig'
                }
            },
            editForm: {
                readOnly: true,
                buttons:  {
                    share: null,
                    pdf: null
                },
                generateElementsFromFields: false,
                elements: [
                    { type: 'textbox', label: 'Full Name', binding: 'name' },
                    { type: 'textbox', label: 'Position', binding: 'position' },
                    { type: 'textbox', label: 'Department', binding: 'department' },
                    { type: 'textbox', label: 'Phone', binding: 'phone' },
                    { type: 'textbox', label: 'Email', binding: 'email' },
                ]
            },
            nodeBinding: {
                img_0: "img",
                name: "name",
                position: "position",
                department: "department",
                phone: "phone",
                email: "email"
            },
            nodes: getDataJson()
        });
    }
}

function getDataJson() {
    return [
        { id: '1_1', img: "avatar.png", department: "Департамент по работе с данными", name: "Махпиров Дилшат", position: "Директор департамента", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },
        { id: '1_99', pid: '1_1', tags: ['assistant'], img: "avatar.png", department: "Департамент по работе с данными", name: "Дюсупов Султан", position: "Заместитель директора департамента", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },

        { id: '1_d1_2', pid: '1_1', tags: ['group'], img: "avatar.png", department: "Отдел автоматизации отчетности", name: "Нет руководителя", position: "Нет руководителя" },
        { id: '1_d1_7', stpid: '1_d1_2', tags: ['group'], img: "avatar.png", position: "Главный специалист", name: "Ким Юля", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },
        { id: '1_d1_8', stpid: '1_d1_2', tags: ['group'], img: "avatar.png", position: "Главный специалист", name: "Тошбаев Бахромжон", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },
        { id: '1_d1_9', stpid: '1_d1_2', tags: ['group'], img: "avatar.png", position: "Главный специалист", name: "Трушев Очир", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },
        { id: '1_d1_10', stpid: '1_d1_2', tags: ['group'], img: "avatar.png", position: "Главный специалист", name: "Худайбердиев Лазиз", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },


        { id: '1_d2_3', pid: '1_1', tags: ['group'], img: "avatar.png", department: "Отдел продуктовой разработки", name: "Нет руководителя", position: "Нет руководителя" },
        { id: '1_d2_11', stpid: '1_d2_3', tags: ['group'], img: "avatar.png", position: "Главный специалист", name: "Носиров Боситхон", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },
        { id: '1_d2_12', stpid: '1_d2_3', tags: ['group'], img: "avatar.png", position: "Главный специалист", name: "Рашитов Марат", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },
        { id: '1_d2_13', stpid: '1_d2_3', tags: ['group'], img: "avatar.png", position: "Главный специалист", name: "Сайфиев Севинч", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },
        { id: '1_d2_14', stpid: '1_d2_3', tags: ['group'], img: "avatar.png", position: "Главный специалист", name: "Чуракова Мария", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },
        { id: '1_d2_15', stpid: '1_d2_3', tags: ['group'], img: "avatar.png", position: "Главный специалист", name: "Юсупов Достон", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },



        { id: '1_d3_4', pid: '1_1', tags: ['group'], img: "avatar.png", department: "Отдел управление инженерии данных", name: "Нет руководителя", position: "Нет руководителя" },

        { id: '1_d4_5', stpid: '1_d3_4', tags: ['innerTree'], img: "avatar.png", department: "Отдел обработки данных", name: "Амиреев Дархан", position: "Началальник отдела", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },
        { id: '1_d4_16', pid: '1_d4_5', tags: ['group'], img: "avatar.png", position: "Главный специалист", name: "Агапова Мария", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },
        { id: '1_d4_17', pid: '1_d4_5', tags: ['group'], img: "avatar.png", position: "Главный специалист", name: "Дуранидис Фёдер", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },
        { id: '1_d4_18', pid: '1_d4_5', tags: ['group'], img: "avatar.png", position: "Главный специалист", name: "Елубаева Гулмира", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },
        { id: '1_d4_19', pid: '1_d4_5', tags: ['group'], img: "avatar.png", position: "Главный специалист", name: "Омаров Ринат", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },
        { id: '1_d4_20', pid: '1_d4_5', tags: ['group'], img: "avatar.png", position: "Главный специалист", name: "Сайгафаров Тимур", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },

        { id: '1_d5_6', stpid: '1_d3_4', tags: ['innerTree'], img: "avatar.png", department: "Отдел разработки и сопровождения систем отчетности", name: "Исмоилов Равшан", position: "Началальник отдела", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" },
        { id: '1_d5_21', pid: '1_d5_6', tags: ['group'], img: "avatar.png", position: "Главный специалист", name: "Асланов Аслиддин", phone: "+99890-123-45-67", email: "test@kapitalbank.uz" }
    ];
}

customElements.define("org-chart", Orgchart);