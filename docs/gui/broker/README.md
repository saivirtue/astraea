### broker

`client`頁面提供查看 `basic` `config` `metrics` 與 `alter`等資訊

- [basic](#basic)
- [config](#config)
- [metrics](#metrics)
- [alter](#alter)

### basic
`basic` 可以查詢所有或特定`broker/host`的基本資訊

![broker-basic 1](broker_basic_1.png)
*顯示所有`broker`資訊*

![broker-basic 2](broker_basic_2.png)
*指定特定`broker id`的資訊*


### config

可以查詢`broker`內的各項`config`，搜尋部分支援正規表示法，可以進一步過濾查詢資料

![broker-config 1](broker_config_1.png)
*不指定`config key`*

![broker-config 2](broker_config_2.png)
*指定兩個`key`的寫法*


### metrics

可以取得`broker`的各項指標，搜尋可支援正規表示法

指標種類有以下項目:

- host
- controller
- controller state
- network
- delayed operation
- replica
- broker topic

![broker-metrics 1](broker_metrics_1.png)
*查看`host`的資訊*

### alter

可以在此頁面檢視與修改 `broker` 的設定

![broker-alter 1](broker_alter_1.png)
*快速查看`broker`資訊，並且可以透過下方按鈕變更*