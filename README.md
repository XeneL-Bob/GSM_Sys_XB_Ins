# Grocery Stock Management System (CSC201 Task 1)

Hello Hello, This is a command-line simulator that processes a history of grocery operations (stocking, orders, returns, expiry, discounts) and computes the final **profit/loss**. The input comes from text files; The commands are executed in order. Output lists current item quantities (`CHECK`) and the total profit/loss (`PROFIT`).

> Final package namespace used here: `xi.gsms`.

---
## to Build and run a quick batch run

For convenience you can copy and paste the following lines into your terminal which should then give you a complete build and a batch run of the xample with all inputs (see below):

#########
cd G:\Git\GSM_Sys_XB_Ins
rmdir /s /q out 2>nul & mkdir out
del sources.txt 2>nul
for /R src\main\java %f in (*.java) do @echo %f>>sources.txt
javac -d out @sources.txt

for %F in (src\main\input\*.txt) do @echo === %~nxF === & java -cp out xi.gsms.Main "%F" & echo.
##########
---

## Features

* **FIFO** inventory consumption for `ORDER` and `EXPIRE`.
* **Discount stack** per item (`DISCOUNT` / `DISCOUNT_END`), only the most recent discount is active.
* **Returns** reverse profits without changing inventory:

  * **LIFO by sale** for the same base sell price.
  * **FIFO by cost segments** inside each sale (to correctly cancel profit when orders consumed multiple stock batches).
* **Invalid cases** trigger `Profit/Loss: NA`. (Per spec, if NA occurs, item counts are not graded.)

---

## Supported Cmds

```
STOCK <item> <quantity> <cost_price>
ORDER <item> <quantity> <sell_price>
EXPIRE <item> <quantity>
RETURN <item> <quantity> <sell_price>
DISCOUNT <item> <discount_percentage>
DISCOUNT_END <item>
CHECK
PROFIT
```

### Notes

* `STOCK`: cost must be **> 0**. (Stocking itself is not an expense.)
* `ORDER`: sell price must be **>= 0**. Applies current discount if any.
* `EXPIRE`: removes items FIFO; counts cost as **loss**.
* `RETURN`: targets matching **base** sell price (before discount), reverses most recent matching sale(s); **does not** add back to stock.
* `DISCOUNT_END`: removes the most recent discount for that item (ignore if none).
* `CHECK`: prints `<item>: <quantity>` per item (deterministic order).
* `PROFIT`: prints either `Profit/Loss: $<amount>` (two decimals) or `Profit/Loss: NA`.

### Invalid → `NA` when any of these occur

* `ORDER`/`EXPIRE` more than available stock.
* `RETURN` more than sold at the specified **sell price**.
* `ORDER` with negative sell price.
* `STOCK` with cost **<= 0**.

---

## Repository Layout

```
/src
  /main
    /java
      /xi/gsms/
        Main.java
        Processor.java
        Model.java
    /input/
        example.txt
        input_01.txt
        input_02.txt
        input_03.txt
        input_04.txt
        input_05.txt
input_files.txt     (optional list of input paths, one per line)
```

---

## Prerequisites

* Java 17+ (Java 11+ also OK).
* Windows CMD/PowerShell, macOS, or Linux shell.

---

## Build (compile)

### Windows CMD

```bat
cd G:\Git\GSM_Sys_XB_Ins
rmdir /s /q out 2>nul & mkdir out
del sources.txt 2>nul
for /R src\main\java %f in (*.java) do @echo %f>>sources.txt
javac -d out @sources.txt
```

### PowerShell

```powershell
Remove-Item -Recurse -Force out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force out | Out-Null
$src = Get-ChildItem -Recurse -Filter *.java src\main\java | Select-Object -Expand FullName
javac -d out $src
```

### macOS/Linux

```bash
rm -rf out && mkdir -p out
find src/main/java -name "*.java" > sources.txt
javac -d out @sources.txt
```

---

## Run

### Single input file

```bat
java -cp out xi.gsms.Main src\main\input\example.txt
```

### Multiple files (one-by-one)

```bat
java -cp out xi.gsms.Main src\main\input\input_01.txt
java -cp out xi.gsms.Main src\main\input\input_02.txt
...
```

### Batch all `.txt` inputs (Windows CMD)

```bat
for %F in (src\main\input\*.txt) do @echo === %~nxF === ^& java -cp out xi.gsms.Main "%F" ^& echo.
```

> In a `.bat` file, use `%%F` instead of `%F`.

### Batch via `input_files.txt`

Create `input_files.txt` at repo root:

```
src/main/input/example.txt
src/main/input/input_01.txt
src/main/input/input_02.txt
...
```

Run:

```bat
java -cp out xi.gsms.Main
```

---


## Sample / Expected Outputs

* `example.txt` →

  ```
  Apple: 5
  Peer: 20
  Profit/Loss: $74.00
  ```
* `input_05.txt` (example invalid case) →

  ```
  Apple: 30        # quantity line optional per spec
  Profit/Loss: NA
  ```

---

## Design Overview

* **Package:** `xi.gsms`
* **Key classes (`Model.java`):**

  * `StockBatch{qty,cost}` — inventory batch (FIFO).
  * `SaleSegment{qty,cost}` — cost segments captured at order time (FIFO within a sale).
  * `SaleRecord{baseSellKey,effectiveSell,qtyRemaining,segmentsFIFO}` — a single ORDER stored for potential returns (LIFO across sales for the same base price).
  * `ItemState{stockFIFO, discountStack, salesByBaseSell, totalQtyInStock}` — per-item state.
  * `Inventory{items, profit, invalid}` — global state for a run.
* **Accounting:**

  * `ORDER`: `profit += (effectiveSell - cost) * qtyTaken`.
  * `EXPIRE`: `profit += - cost * qtyTaken`.
  * `RETURN`: `profit += - (effectiveSell_at_order - cost) * qtyReturned`.

---

## Complexity (per command)

* `STOCK`: **O(1)** time; **O(1)** space per call.
* `ORDER(q)`: **O(b)** where `b` = number of stock batches consumed; space grows with number of sale segments recorded.
* `EXPIRE(q)`: **O(b)**.
* `RETURN(q)`: **O(k + t)** where `k` = number of sale records touched (LIFO), `t` = number of segments traversed (FIFO within sale).
* `DISCOUNT`, `DISCOUNT_END`: **O(1)**.
* `CHECK`: **O(I)** for `I` items.
* Overall space: **O(B + R)** where `B` = remaining stock batches, `R` = live (unreturned) sale segments.

---

## Assumptions (based on the assignment requirements)

* Money values use `double`; output formatted to **two decimals**.
* Only the **latest** discount is active; `DISCOUNT_END` is a no-op if no discount exists.
* When `invalid == true`, the app prints `Profit/Loss: NA`.
  (If you also print `CHECK` lines, the exact quantities don’t matter per spec.)

---

## basic torubleshooting 

* **`Could not find or load main class xi.gsms.Main`**
  Ensure:

  * `package xi.gsms;` is the first line of all source files.
  * Class files exist under `out\xi\gsms\`.
  * Running from repo root: `java -cp out xi.gsms.Main ...`

* **`error: file not found: src\...`**
  The path is wrong. Verify with:

  ```bat
  dir /s /b src\main\java\xi\gsms
  ```

* **Unexpected `NA`**
  Likely triggered by one of the invalid rules (over-order/expire/return, negative sell price, or non-positive stock cost).

---
