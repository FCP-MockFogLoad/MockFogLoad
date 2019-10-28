import io
import json
import os

def extract_mean_temperature(temp_data):
    mean = 0
    count = 0

    return mean / count

mean_temps = {}

for filename in os.listdir("./data"):
    if not filename.endswith(".txt"):
        continue

    temp_data_file = open(filename, 'r')
    temp_data = temp_data_file.read()
    mean_temp = extract_mean_temperature(temp_data)

    city = filename.replace(".txt", "")
    mean_temps[city] = mean_temp

with open("./mean_temp.json", 'w') as result_file:
    json.dump(mean_temps, result_file)
